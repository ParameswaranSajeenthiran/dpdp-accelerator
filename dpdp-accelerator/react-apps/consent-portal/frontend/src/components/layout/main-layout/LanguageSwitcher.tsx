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

import { Button, ListItemText, Menu, MenuItem, Tooltip } from '@wso2/oxygen-ui'
import { Check, Languages } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import useLanguage from '../../../i18n/useLanguage'

function LanguageSwitcher(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { current, languages, setLanguage } = useLanguage()
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const open = Boolean(anchor)

  return (
    <>
      <Tooltip title={t('language.label')}>
        <Button
          variant="text"
          color="inherit"
          startIcon={<Languages size={16} />}
          aria-label={t('language.selectAria')}
          aria-haspopup="menu"
          aria-expanded={open}
          onClick={(event) => setAnchor(event.currentTarget)}
          sx={{ textTransform: 'none' }}
        >
          {current.endonym}
        </Button>
      </Tooltip>
      <Menu
        anchorEl={anchor}
        open={open}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { sx: { maxHeight: 360 } } }}
      >
        {languages.map((language) => (
          <MenuItem
            key={language.code}
            selected={language.code === current.code}
            onClick={() => {
              setLanguage(language.code)
              setAnchor(null)
            }}
          >
            <ListItemText
              primary={language.endonym}
              secondary={language.endonym === language.english ? undefined : language.english}
            />
            {language.code === current.code ? <Check size={16} /> : null}
          </MenuItem>
        ))}
      </Menu>
    </>
  )
}

export default LanguageSwitcher
