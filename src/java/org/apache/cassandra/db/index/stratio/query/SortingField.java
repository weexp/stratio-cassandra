/*
 * Copyright 2014, Stratio.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.db.index.stratio.query;

import org.apache.cassandra.db.index.stratio.schema.CellMapper;
import org.apache.cassandra.db.index.stratio.schema.Schema;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * A sorting for a field of a search.
 * 
 * @author Andres de la Pena <adelapena@stratio.com>
 * 
 */
public class SortingField {

	private static final boolean DEFAULT_REVERSE = false;

	private String field;
	private boolean reverse;

	@JsonCreator
	public SortingField(@JsonProperty("field") String field, @JsonProperty("reverse") Boolean reverse) {
		this.field = field;
		this.reverse = reverse == null ? DEFAULT_REVERSE : reverse;
	}

	public SortField sortField(Schema schema) {
		if (field == null || field.trim().isEmpty()) {
			throw new IllegalArgumentException("Field name required");
		}
		CellMapper<?> cellMapper = schema.getMapper(field);
		if (cellMapper == null) {
			return new SortField(field, Type.STRING, reverse);
		} else {
			return cellMapper.sortField(field, reverse);
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Sort [field=");
		builder.append(field);
		builder.append(", reverse=");
		builder.append(reverse);
		builder.append("]");
		return builder.toString();
	}

}
