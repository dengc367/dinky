/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.dinky.metadata.constant;

public interface KyuubiConstant {

    /** SQL helpers (Kyuubi/Hive compatible) */
    String QUERY_ALL_DATABASE = "show databases";
    /** 指定 schema 下全部表（显式 in，无需先 use） */
    String QUERY_ALL_TABLES_BY_SCHEMA = "show tables in `%s`";
    /** 按库名 + 表名模糊匹配 */
    String QUERY_ALL_TABLES_BY_SCHEMA_NAME_AND_TABLE_NAME = "show tables in `%s` like '%s'";
    String DESCRIBE_TABLE = "describe `%s`.`%s`";
}

