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

import { l } from '@/utils/intl';
import { ProFormGroup, ProFormList, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import { Divider, Space } from 'antd';
import React from 'react';
import { Form } from 'antd';

const AuthTypeOptions = [
  { label: 'NONE', value: 'NONE' },
  { label: 'BASIC', value: 'BASIC' }
];

const EngineTypeOptions = [
  { label: 'SPARK', value: 'SPARK' },
  { label: 'FLINK', value: 'FLINK' }
];

const RunModeOptions = [
  { label: 'SESSION', value: 'SESSION' },
  { label: 'BATCH', value: 'BATCH' }
];

const KyuubiConfig: React.FC = () => {
  const authType = Form.useWatch(['config', 'authType']);

  return (
    <>
      <Divider>Kyuubi Gateway</Divider>
      <ProFormGroup>
        <ProFormText
          name={['config', 'host']}
          label='REST Endpoint'
          width='lg'
          rules={[{ required: true, message: 'Please input Kyuubi REST endpoint' }]}
          placeholder='http://localhost:10099'
        />
        <ProFormSelect
          name={['config', 'runMode']}
          label='Run Mode'
          width='md'
          options={RunModeOptions}
          rules={[{ required: true, message: 'Please select run mode' }]}
        />
        <ProFormSelect
          name={['config', 'engineType']}
          label='Engine Type'
          width='md'
          options={EngineTypeOptions}
          rules={[{ required: true, message: 'Please select engine type' }]}
        />
      </ProFormGroup>

      <ProFormGroup>
        <ProFormSelect
          name={['config', 'authType']}
          label='Auth Type'
          width='md'
          options={AuthTypeOptions}
          rules={[{ required: true, message: 'Please select auth type' }]}
        />
        <ProFormText
          name={['config', 'profile']}
          label='Configuration Profile'
          width='lg'
          tooltip='对应 kyuubi.session.conf.profile，用于加载服务端预定义配置组'
          placeholder='test-flink'
        />
      </ProFormGroup>

      {authType === 'BASIC' && (
        <ProFormGroup>
          <ProFormText
            name={['config', 'username']}
            label='Username'
            width='md'
            rules={[{ required: true, message: 'Please input username' }]}
          />
          <ProFormText.Password
            name={['config', 'password']}
            label='Password'
            width='md'
            rules={[{ required: true, message: 'Please input password' }]}
          />
        </ProFormGroup>
      )}

      <Divider>{l('rc.cc.flink.defineConfig')}</Divider>
      <ProFormList
        name={['config', 'conf']}
        copyIconProps={false}
        deleteIconProps={{ tooltipText: l('rc.cc.deleteConfig') }}
        creatorButtonProps={{ style: { width: '100%' }, creatorButtonText: l('rc.cc.addConfig') }}
      >
        <ProFormGroup key='confGroup' style={{ width: '100%' }}>
          <Space key='config' style={{ width: '100%' }} align='baseline'>
            <ProFormText width={'md'} name='key' placeholder={l('rc.cc.key')} />
            <ProFormText width={'sm'} name='value' placeholder={l('rc.cc.value')} />
          </Space>
        </ProFormGroup>
      </ProFormList>
    </>
  );
};

export default KyuubiConfig;

