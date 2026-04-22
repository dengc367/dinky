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

import { ProForm, ProFormDependency, ProFormSegmented, ProFormText, ProFormTextArea } from '@ant-design/pro-components';
import React from 'react';

type Props = {
  // keep signature consistent with other datasource forms
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  form?: any;
};

const AUTH = {
  BASIC: 'basic',
  OAUTH2_CLIENT_CREDENTIALS: 'oauth2_client_credentials'
};

const GravitinoSourceForm: React.FC<Props> = () => {
  return (
    <div>
      <ProForm.Group>
        <ProFormText
          name={['connectConfig', 'endpoint']}
          width={'md'}
          label={'Endpoint'}
          rules={[{ required: true, message: 'Please input Gravitino endpoint' }]}
          placeholder={'https://gravitino:8090'}
        />
        <ProFormText
          name={['connectConfig', 'metalake']}
          width={'md'}
          label={'Metalake'}
          rules={[{ required: true, message: 'Please input metalake' }]}
          placeholder={'prod'}
        />
      </ProForm.Group>

      <ProForm.Group>
        <ProFormTextArea
          name={['connectConfig', 'catalogWhitelist']}
          label={'Catalog Whitelist (optional)'}
          placeholder={'iceberg_prod\npaimon_rt'}
          fieldProps={{ autoSize: { minRows: 2, maxRows: 6 } }}
        />
        <ProFormText
          name={['connectConfig', 'catalogNameRegex']}
          width={'md'}
          label={'Catalog Name Regex (optional)'}
          placeholder={'^(iceberg|paimon)_.*$'}
        />
      </ProForm.Group>

      <ProForm.Group>
        <ProFormSegmented
          name={['connectConfig', 'authType']}
          label={'Auth'}
          required
          initialValue={AUTH.BASIC}
          request={async () => [
            { label: 'Basic', value: AUTH.BASIC, disabled: false },
            { label: 'OAuth2 (client_credentials)', value: AUTH.OAUTH2_CLIENT_CREDENTIALS, disabled: false }
          ]}
        />
      </ProForm.Group>

      <ProFormDependency name={[['connectConfig', 'authType']]}>
        {({ connectConfig }) => {
          const authType = connectConfig?.authType ?? AUTH.BASIC;
          if (authType === AUTH.BASIC) {
            return (
              <ProForm.Group>
                <ProFormText
                  name={['connectConfig', 'username']}
                  width={'sm'}
                  label={'Username'}
                  rules={[{ required: true, message: 'Please input username' }]}
                />
                <ProFormText.Password
                  name={['connectConfig', 'password']}
                  width={'sm'}
                  label={'Password'}
                />
              </ProForm.Group>
            );
          }
          if (authType === AUTH.OAUTH2_CLIENT_CREDENTIALS) {
            return (
              <>
                <ProForm.Group>
                  <ProFormText
                    name={['connectConfig', 'tokenUrl']}
                    width={'md'}
                    label={'Token URL'}
                    rules={[{ required: true, message: 'Please input tokenUrl' }]}
                    placeholder={'https://oauth.example.com/token'}
                  />
                  <ProFormText
                    name={['connectConfig', 'scope']}
                    width={'md'}
                    label={'Scope/Audience'}
                    placeholder={'serviceAudience'}
                  />
                </ProForm.Group>
                <ProForm.Group>
                  <ProFormText
                    name={['connectConfig', 'clientId']}
                    width={'md'}
                    label={'Client ID'}
                    rules={[{ required: true, message: 'Please input clientId' }]}
                  />
                  <ProFormText.Password
                    name={['connectConfig', 'clientSecret']}
                    width={'md'}
                    label={'Client Secret'}
                    rules={[{ required: true, message: 'Please input clientSecret' }]}
                  />
                </ProForm.Group>
              </>
            );
          }
          return null;
        }}
      </ProFormDependency>
    </div>
  );
};

export default GravitinoSourceForm;

