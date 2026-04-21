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
  ProFormList,
  ProFormSelect,
  ProFormText,
  ProFormTextArea
} from '@ant-design/pro-components';
import { l } from '@/utils/intl';
import React, { useEffect } from 'react';
import { AutoComplete, Form } from 'antd';
import { AUTO_COMPLETE_TYPE } from '@/pages/RegCenter/DataSource/components/constants';
import TextArea from 'antd/es/input/TextArea';
import { FormInstance } from 'antd/es/form/hooks/useForm';
import { Values } from 'async-validator';

type Props = {
  form: FormInstance<Values>;
};

const MODE = {
  SESSION_JDBC: 'SESSION_JDBC',
  BATCH_REST: 'BATCH_REST'
};

const KyuubiSourceForm: React.FC<Props> = (props) => {
  const { form } = props;
  const [mode, setMode] = React.useState<string>(MODE.SESSION_JDBC);

  useEffect(() => {
    const v = form.getFieldsValue()?.connectConfig?.mode;
    if (v) {
      setMode(v);
    }
  });

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
            rules={[{ required: true, message: l('rc.ds.urlPlaceholder') }]}
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
              <TextArea placeholder={l('rc.ds.urlPlaceholder')} />
            </AutoComplete>
          </Form.Item>
        </ProFormGroup>
      </>
    );
  };

  const renderRest = () => {
    return (
      <>
        <ProFormText
          name={['connectConfig', 'username']}
          width={'sm'}
          label={l('rc.ds.username')}
          tooltip={'REST Basic Auth username'}
          rules={[{ required: true, message: l('rc.ds.usernamePlaceholder') }]}
          placeholder={l('rc.ds.usernamePlaceholder')}
        />
        <ProFormText.Password
          name={['connectConfig', 'password']}
          width={'sm'}
          label={l('rc.ds.password')}
          tooltip={'REST Basic Auth password'}
          placeholder={l('rc.ds.passwordPlaceholder')}
        />
        <ProFormText
          name={['connectConfig', 'endpoint']}
          width={'md'}
          label={'endpoint'}
          rules={[{ required: true, message: 'endpoint is required' }]}
          placeholder={'http://host:10099'}
        />
        <ProFormText name={['connectConfig', 'batchType']} width={'sm'} label={'batchType'} placeholder={'SPARK'} />
        <ProFormText name={['connectConfig', 'resource']} width={'md'} label={'resource'} />
        <ProFormText name={['connectConfig', 'className']} width={'md'} label={'className'} />
        <ProFormText name={['connectConfig', 'name']} width={'md'} label={'name'} />

        <ProFormList
          label={'conf'}
          name={['connectConfig', 'conf']}
          copyIconProps={false}
          creatorButtonProps={{ creatorButtonText: l('rc.cc.addConfig') }}
        >
          <ProFormGroup key='confGroup' style={{ width: '100%' }}>
            <ProFormText name='key' width={'md'} placeholder={l('rc.cc.key')} />
            <ProFormText name='value' width={'xl'} placeholder={l('rc.cc.value')} />
          </ProFormGroup>
        </ProFormList>

        <ProFormList
          label={'args'}
          name={['connectConfig', 'args']}
          copyIconProps={false}
          creatorButtonProps={{ creatorButtonText: l('rc.cc.addConfig') }}
        >
          <ProFormGroup key='argsGroup' style={{ width: '100%' }}>
            <ProFormText name='value' width={'xl'} placeholder={'arg'} />
          </ProFormGroup>
        </ProFormList>

        <ProFormList
          label={'sessionConfigs'}
          name={['connectConfig', 'sessionConfigs']}
          copyIconProps={false}
          creatorButtonProps={{ creatorButtonText: l('rc.cc.addConfig') }}
        >
          <ProFormGroup key='sessionConfigsGroup' style={{ width: '100%' }}>
            <ProFormText name='key' width={'md'} placeholder={l('rc.cc.key')} />
            <ProFormText name='value' width={'xl'} placeholder={l('rc.cc.value')} />
          </ProFormGroup>
        </ProFormList>
      </>
    );
  };

  return (
    <div>
      <ProFormSelect
        name={['connectConfig', 'mode']}
        width={'sm'}
        label={'mode'}
        initialValue={MODE.SESSION_JDBC}
        options={[
          { label: MODE.SESSION_JDBC, value: MODE.SESSION_JDBC },
          { label: MODE.BATCH_REST, value: MODE.BATCH_REST }
        ]}
        fieldProps={{
          onChange: (v) => setMode(v)
        }}
      />
      <ProFormGroup>{mode === MODE.BATCH_REST ? renderRest() : renderJdbc()}</ProFormGroup>
    </div>
  );
};

export default KyuubiSourceForm;

