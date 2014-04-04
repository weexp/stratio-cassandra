package org.apache.cassandra.db.index.stratio;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.db.ColumnFamily;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.index.PerRowSecondaryIndex;
import org.apache.cassandra.db.index.SecondaryIndexManager;
import org.apache.cassandra.db.index.SecondaryIndexSearcher;
import org.apache.cassandra.db.index.stratio.util.Log;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.exceptions.ConfigurationException;

/**
 * A {@link PerRowSecondaryIndex} that uses Apache Lucene as backend. It allows, among others,
 * multi-comun and full-text search.
 * 
 * @author adelapena
 * 
 */
public class RowIndex extends PerRowSecondaryIndex {

	private SecondaryIndexManager secondaryIndexManager;
	private CFMetaData metadata;
	private ColumnDefinition columnDefinition;

	private String keyspaceName;
	private String tableName;
	private String indexName;
	private String columnName;
	private String logName;

	private RowService rowService;

	// Concurrency lock
	private ReadWriteLock lock = new ReentrantReadWriteLock();

	@Override
	public String getIndexName() {
		return indexName;
	}

	public String getKeyspaceName() {
		return keyspaceName;
	}

	public String getTableName() {
		return tableName;
	}

	public String getColumnName() {
		return columnName;
	}

	@Override
	public void init() {
		Log.info("Initializing index %s.%s.%s", keyspaceName, tableName, indexName);
		lock.readLock().lock();
		try {
			setup();
		} finally {
			lock.readLock().unlock();
		}
	}

	private void setup() {

		// Load column family info
		secondaryIndexManager = baseCfs.indexManager;
		metadata = baseCfs.metadata;
		columnDefinition = columnDefs.iterator().next();
		indexName = columnDefinition.getIndexName();
		keyspaceName = metadata.ksName;
		tableName = metadata.cfName;
		columnName = UTF8Type.instance.compose(columnDefinition.name);
		logName = String.format("%s.%s.%s", keyspaceName, tableName, indexName);

		// Build row mapper
		rowService = new RowService(baseCfs, columnDefinition);
	}

	/**
	 * Index the given row.
	 * 
	 * @param key
	 *            The partition key.
	 * @param columnFamily
	 *            The column family data to be indexed
	 */
	@Override
	public void index(ByteBuffer key, ColumnFamily columnFamily) {
		Log.debug("Indexing row %s in index %s", key, logName);
		lock.readLock().lock();
		try {
			rowService.index(key, columnFamily);
		} catch (Exception e) { // Ignore errors
			Log.error(e, "Ignoring error while indexing row %s", key);
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * cleans up deleted columns from cassandra cleanup compaction
	 * 
	 * @param key
	 */
	@Override
	public void delete(DecoratedKey key) {
		Log.debug("Removing row %s from index %s", key, logName);
		lock.writeLock().lock();
		try {
			rowService.delete(key);
			rowService = null;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public boolean indexes(ByteBuffer cellName) {
		return true;
	}

	public static void validate(CFMetaData metadata, String indexName, Map<String, String> options) {
		new RowServiceConfig(metadata, indexName, options);
	}

	@Override
	public void validateOptions() throws ConfigurationException {
		Log.debug("Validating");
		try {
			ColumnDefinition columnDefinition = columnDefs.iterator().next();
			if (baseCfs != null) {
				new RowServiceConfig(baseCfs.metadata,
				                     columnDefinition.getIndexName(),
				                     columnDefinition.getIndexOptions());
				Log.debug("Index options are valid");
			} else {
				Log.debug("Validation skipped");
			}
		} catch (Exception e) {
			String message = "Error while validating index options: " + e.getMessage();
			Log.error(e, message);
			throw new ConfigurationException(message, e);
		}
	}

	@Override
	public long getLiveSize() {
		return 0;
	}

	@Override
	public ColumnFamilyStore getIndexCfs() {
		return null;
	}

	@Override
	public void removeIndex(ByteBuffer columnName) {
		Log.info("Removing index %s", logName);
		lock.writeLock().lock();
		try {
			rowService.delete();
			rowService = null;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void invalidate() {
		Log.info("Invalidating index %s", logName);
		rowService.delete();
		rowService = null;
	}

	@Override
	public void truncateBlocking(long truncatedAt) {
		Log.info("Truncating index %s", logName);
		rowService.truncate();
	}

	@Override
	public void reload() {
		Log.info("Reloading index %s", logName);
		lock.writeLock().lock();
		try {
			if (rowService == null) {
				setup();
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void forceBlockingFlush() {
		Log.info("Flushing index %s", logName);
		lock.writeLock().lock();
		try {
			rowService.commit();
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	protected SecondaryIndexSearcher createSecondaryIndexSearcher(Set<ByteBuffer> columns) {
		Log.debug("Searching index %s", logName);
		return new RowIndexSearcher(secondaryIndexManager, this, columns, rowService);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("RowIndex [index=");
		builder.append(indexName);
		builder.append(", keyspace=");
		builder.append(keyspaceName);
		builder.append(", table=");
		builder.append(tableName);
		builder.append(", column=");
		builder.append(columnName);
		builder.append("]");
		return builder.toString();
	}

}