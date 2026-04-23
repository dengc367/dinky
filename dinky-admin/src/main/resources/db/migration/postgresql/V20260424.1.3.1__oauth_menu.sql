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

INSERT INTO public.dinky_sys_menu (id, parent_id, name, path, component, perms, icon, type, display, order_num,
                                   create_time, update_time, note)
VALUES (179, 24, 'OAuth 配置', '/settings/globalsetting/oauth', null, 'settings:globalsetting:oauth',
        'SettingOutlined', 'F', 0, 124, '2026-04-23 12:00:00', '2026-04-23 12:00:00', null);

INSERT INTO public.dinky_sys_menu (id, parent_id, name, path, component, perms, icon, type, display, order_num,
                                   create_time, update_time, note)
VALUES (180, 179, '编辑', '/settings/globalsetting/oauth/edit', null, 'settings:globalsetting:oauth:edit',
        'EditOutlined', 'F', 0, 125, '2026-04-23 12:00:00', '2026-04-23 12:00:00', null);
