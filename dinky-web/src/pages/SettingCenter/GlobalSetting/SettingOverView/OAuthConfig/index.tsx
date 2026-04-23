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

import GeneralConfig from '@/pages/SettingCenter/GlobalSetting/SettingOverView/GeneralConfig';
import { GeneralComponentConfigProps } from '@/pages/SettingCenter/GlobalSetting/data.d';
import { API_CONSTANTS } from '@/services/endpoints';
import { postAll } from '@/services/api';
import { RESPONSE_CODE } from '@/services/constants';
import { BaseConfigProperties } from '@/types/SettingCenter/data';
import { HasAuthority } from '@/hooks/useAccess';
import { l } from '@/utils/intl';
import { Button, Space, Spin, Tag, message } from 'antd';
import React from 'react';

export const OAuthConfig = ({ data, onSave, auth }: GeneralComponentConfigProps) => {
  const [loading, setLoading] = React.useState(false);
  const [syncing, setSyncing] = React.useState(false);

  const onSaveHandler = async (dataConfig: BaseConfigProperties) => {
    setLoading(true);
    await onSave(dataConfig);
    setLoading(false);
  };

  const onSync = async () => {
    setSyncing(true);
    const hide = message.loading(l('app.request.running') + l('sys.oauth.settings.sync'), 0);
    try {
      const res = await postAll(API_CONSTANTS.OAUTH_SYNC, {});
      hide();
      if (res.code === RESPONSE_CODE.SUCCESS) {
        message.success(l('sys.oauth.settings.sync.success'));
      } else {
        message.warning(res.msg);
      }
    } catch (e) {
      hide();
      message.error(l('app.request.error'));
    } finally {
      setSyncing(false);
    }
  };

  return (
    <Spin spinning={syncing}>
      <GeneralConfig
        loading={loading}
        onSave={onSaveHandler}
        auth={auth}
        toolBarRender={() => [
          <Button
            key='oauth-sync'
            type='primary'
            disabled={!HasAuthority(auth) || syncing}
            loading={syncing}
            onClick={onSync}
          >
            {l('sys.oauth.settings.sync')}
          </Button>
        ]}
        tag={
          <Space size={4}>
            <Tag color={'default'}>{l('sys.setting.tag.integration')}</Tag>
          </Space>
        }
        data={data}
      />
    </Spin>
  );
};
