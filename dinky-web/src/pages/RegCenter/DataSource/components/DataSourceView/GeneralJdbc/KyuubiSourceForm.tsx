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

import {
  ProFormGroup,
  ProFormText,
  ProFormTextArea
} from '@ant-design/pro-components';
import { l } from '@/utils/intl';
import React from 'react';
import { AutoComplete, Form } from 'antd';
import { AUTO_COMPLETE_TYPE } from '@/pages/RegCenter/DataSource/components/constants';
import TextArea from 'antd/es/input/TextArea';
import { FormInstance } from 'antd/es/form/hooks/useForm';
import { Values } from 'async-validator';

type Props = {
  form: FormInstance<Values>;
};

const KyuubiSourceForm: React.FC<Props> = (props) => {
  const { form } = props;

  const renderJdbc = () => {
    return (
      <>
        <ProFormText
          name={['connectConfig', 'username']}
          width={'sm'}
          label={l('rc.ds.username')}
          rules={[{ required: true, message: l('rc.ds.usernamePlaceholder') }]}
          placeholder={l('rc.ds.usernamePlaceholder')}
        />
        <ProFormText.Password
          name={['connectConfig', 'password']}
          width={'sm'}
          label={l('rc.ds.password')}
          placeholder={l('rc.ds.passwordPlaceholder')}
        />
        <ProFormGroup>
          <Form.Item
            name={['connectConfig', 'url']}
            label={l('rc.ds.url')}
            rules={[
              { required: true, message: l('rc.ds.urlPlaceholder') },
              { pattern: /^jdbc:kyuubi:\/\//, message: 'Kyuubi URL must start with jdbc:kyuubi://' }
            ]}
          >
            <AutoComplete
              virtual
              placement={'topLeft'}
              autoClearSearchValue
              options={AUTO_COMPLETE_TYPE}
              style={{
                width: parent.innerWidth / 2 - 80
              }}
              filterOption
              onSelect={(value) => form && form.setFieldsValue({ url: value })}
            >
              <TextArea placeholder={'jdbc:kyuubi://host:10009/;'} />
            </AutoComplete>
          </Form.Item>
        </ProFormGroup>
      </>
    );
  };

  return (
    <div>
      <ProFormGroup>{renderJdbc()}</ProFormGroup>
    </div>
  );
};

export default KyuubiSourceForm;

