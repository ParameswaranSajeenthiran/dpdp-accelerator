/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import {
  Box,
  Card,
  CardContent,
  CardHeader,
  Divider,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'

interface ConsentPropertiesSectionProps {
  properties?: Record<string, string>
}

function ConsentPropertiesSection({
  properties,
}: ConsentPropertiesSectionProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const propertyEntries = Object.entries(properties ?? {})

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Typography variant="h5" fontWeight={600}>
            {t('catalog.fields.properties')}
          </Typography>
        }
        sx={{ pb: 1 }}
      />
      <Divider />
      {propertyEntries.length > 0 ? (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 700, width: '35%' }}>
                  {t('catalog.fields.propertyKey')}
                </TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{t('catalog.fields.propertyValue')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {propertyEntries.map(([key, value]) => (
                <TableRow key={key} hover>
                  <TableCell>
                    <Box component="code">{key}</Box>
                  </TableCell>
                  <TableCell sx={{ overflowWrap: 'anywhere' }}>{value}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      ) : (
        <CardContent>
          <Typography variant="body2" color="text.secondary">
            {t('consentRegistry.details.noProperties')}
          </Typography>
        </CardContent>
      )}
    </Card>
  )
}

ConsentPropertiesSection.defaultProps = {
  properties: undefined,
}

export default ConsentPropertiesSection
