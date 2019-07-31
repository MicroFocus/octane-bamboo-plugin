import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.UUID;

/*
 *     Copyright 2017 EntIT Software LLC, a Micro Focus company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
/*
package ut.com.hp.octane.plugins.bamboo.ui;

import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import com.hp.octane.plugins.bamboo.api.OctaneConfigurationKeys;
import com.hp.octane.plugins.bamboo.ui.ConfigureOctaneAction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class ConfigureOctaneActionTest {}

	@Mock
    PluginSettingsFactory settingsFactory;
	@Mock
    PluginSettings settings;

	@Mock
    BambooPermissionManager bambooPermissionManager;

	@Captor
    ArgumentCaptor<String> propertyNameCaptor;

	@Captor
	ArgumentCaptor<String> valueCaptor;

	ConfigureOctaneAction underTest;

	String[] keys = new String[]{
			OctaneConfigurationKeys.UUID,
			OctaneConfigurationKeys.LOCATION,
			OctaneConfigurationKeys.CLIENT_ID,
			OctaneConfigurationKeys.API_SECRET,
			OctaneConfigurationKeys.IMPERSONATION_USER
	};

	String[] values = new String[]{UUID.randomUUID().toString(), "url", "accessKey", "apiSecret", "admin"};

	@Before
	public void setUp() {
		Mockito.when(settingsFactory.createGlobalSettings()).thenReturn(settings);
        Mockito.when(bambooPermissionManager.hasGlobalPermission(Mockito.<BambooPermission>any())).thenReturn(true);

		underTest = new ConfigureOctaneAction(settingsFactory, bambooPermissionManager);

		underTest.setUuid(values[0]);
		underTest.setOctaneUrl(values[1]);
		underTest.setAccessKey(values[2]);
		underTest.setApiSecret(values[3]);
		underTest.setUserName(values[4]);
	}

	@Test
	public void testPropertiesLoaded() {

		underTest.doEdit();
		Mockito.verify(settings, Mockito.times(keys.length)).get(propertyNameCaptor.capture());
		Assert.assertArrayEquals(keys, propertyNameCaptor.getAllValues().toArray());
	}

	@Test
	@Ignore
	public void testPropertiesSaved() {
		underTest.doSave();
		Mockito.verify(settings, Mockito.times(keys.length)).put(propertyNameCaptor.capture(), valueCaptor.capture());
		Assert.assertArrayEquals(keys, propertyNameCaptor.getAllValues().toArray());
		Assert.assertArrayEquals(values, valueCaptor.getAllValues().toArray());
	}
}
*/