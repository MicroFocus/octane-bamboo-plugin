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

package com.hp.octane.plugins.bamboo.octane;

import com.atlassian.bamboo.v2.build.BuildContext;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BuildContextCache {

    private static final long MAX_WAIT_TIME = TimeUnit.MINUTES.toMillis(20);
    private static Map<String, Entry> map = new HashMap();
    private static long lastClearTime = System.currentTimeMillis();
    private static final Logger log = SDKBasedLoggerProvider.getLogger(BuildContextCache.class);

    public static synchronized void add(String planResultKey, BuildContext buildContext) {
        clearOldEntries();
        map.put(planResultKey, new Entry(buildContext));
    }

    private static void clearOldEntries() {
        boolean isToClear = System.currentTimeMillis() > (MAX_WAIT_TIME + lastClearTime);
        if (isToClear) {
            Set<String> keysToDelete = map.entrySet().stream().filter(entry -> entry.getValue().isExceedMaxWaitTime()).map(Map.Entry::getKey).collect(Collectors.toSet());
            log.info(String.format("Clearing %s/%s items", keysToDelete.size(), map.size()));
            keysToDelete.stream().forEach(key -> map.remove(key));
            lastClearTime = System.currentTimeMillis();
        }
    }

    public static BuildContext get(String planResultKey) {
        Entry entry = map.get(planResultKey);
        return entry != null ? entry.getBuildContext() : null;
    }

    private static class Entry {
        private BuildContext buildContext;
        private long insertTime;

        public Entry(BuildContext buildContext) {
            this.buildContext = buildContext;
            this.insertTime = System.currentTimeMillis();
        }

        public BuildContext getBuildContext() {
            return buildContext;
        }

        public long getInsertTime() {
            return insertTime;
        }

        public boolean isExceedMaxWaitTime() {
            long waitTime = System.currentTimeMillis() - insertTime;
            return MAX_WAIT_TIME < waitTime;
        }
    }
}
