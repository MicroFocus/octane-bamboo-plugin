/**
 *
 * Copyright 2017-2023 Open Text
 *
 * The only warranties for products and services of Open Text and
 * its affiliates and licensors (“Open Text”) are as may be set forth
 * in the express warranty statements accompanying such products and services.
 * Nothing herein should be construed as constituting an additional warranty.
 * Open Text shall not be liable for technical or editorial errors or
 * omissions contained herein. The information contained herein is subject
 * to change without notice.
 *
 * Except as specifically indicated otherwise, this document contains
 * confidential information and a valid license is required for possession,
 * use or copying. If this work is provided to the U.S. Government,
 * consistent with FAR 12.211 and 12.212, Commercial Computer Software,
 * Computer Software Documentation, and Technical Data for Commercial Items are
 * licensed to the U.S. Government under vendor's standard commercial license.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package com.hp.octane.plugins.bamboo.octane;

import com.atlassian.bamboo.artifact.MutableArtifact;
import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.build.artifact.*;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionImpl;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionManager;
import com.atlassian.bamboo.util.RequestCacheThreadLocal;
import com.atlassian.bamboo.utils.XsrfUtils;
import com.atlassian.sal.api.component.ComponentLocator;
import com.google.common.io.Files;
import com.hp.octane.integrations.uft.items.UftTestDiscoveryResult;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;


public class ArtifactsHelper {
    private static final Logger log = SDKBasedLoggerProvider.getLogger(ArtifactsHelper.class);
    private static ArtifactLinkManager artifactLinkManager = ComponentLocator.getComponent(ArtifactLinkManager.class);

    public static boolean registerArtifactDefinition(@NotNull Job job, String name, String pattern) {
        if (job == null || org.springframework.util.StringUtils.isEmpty(name) || org.springframework.util.StringUtils.isEmpty(pattern)) {
            return false;
        }
        String HTTP_REQUEST_IS_MUTATIVE_KEY = "bamboo.http.request.isMutative";
        boolean isMutativeKeyWasChanged = false;
        try {
            ArtifactDefinitionManager artifactDefinitionManager = ComponentLocator.getComponent(ArtifactDefinitionManager.class);
            if (artifactDefinitionManager.findArtifactDefinition(job, name) == null) {
                ArtifactDefinitionImpl artifactDefinition = new ArtifactDefinitionImpl(name, "", pattern);
                artifactDefinition.setProducerJob(job);

                //workaround, if request is not mutative - saveArtifactDefinition will fail on XSRF exception
                if (XsrfUtils.areMutativeGetsForbiddenByConfig() && !XsrfUtils.noRequestOrRequestCanMutateState() && !RequestCacheThreadLocal.canRequestMutateState()) {
                    isMutativeKeyWasChanged = true;
                    RequestCacheThreadLocal.getRequestCache().put(HTTP_REQUEST_IS_MUTATIVE_KEY, true);
                }

                //save
                artifactDefinitionManager.saveArtifactDefinition(artifactDefinition);

                //revert workaround
                if (isMutativeKeyWasChanged) {
                    RequestCacheThreadLocal.getRequestCache().put(HTTP_REQUEST_IS_MUTATIVE_KEY, false);
                }
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to registerArtifactDefinition : " + e.getMessage(), e);
        } finally {


        }
        return false;
    }

    public static UftTestDiscoveryResult getTestDiscovery(MutableArtifact artifact,PlanResultKey planResultKey){
        File f;
        try {
            final ArtifactLinkDataProvider dataProvider = artifactLinkManager.getArtifactLinkDataProvider(artifact);
            if (dataProvider instanceof FileSystemArtifactLinkDataProvider) {
                f = getDiscoveryFile((FileSystemArtifactLinkDataProvider) dataProvider);
            } else {
                f = MqmResultsHelper.getDiscoveryFilePath(planResultKey).toFile();
                getDiscoveryFile(dataProvider,f,"");
            }
            if(f != null && f.exists()){
                 return UftTestDiscoveryResult.readFromFile(f);
            }
        } catch (IOException e) {
            logAndThrow(e, "Failed to download artifacts");
        }
        return null;
    }

    private static File getDiscoveryFile(FileSystemArtifactLinkDataProvider dataProvider){
        return Arrays.asList(requireNonNull(dataProvider.getFile().listFiles())).stream()
                .filter(File::isFile)
                .filter(f -> f.getName().contains("uft_discovery_result_build"))
                .findFirst()
                .orElse(null);
    }

    private static void getDiscoveryFile(ArtifactLinkDataProvider dataProvider, File targetDir,String startFrom){

        for (ArtifactFileData data : requireNonNull(dataProvider).listObjects(startFrom)) {
            try {
                if (data instanceof TrampolineArtifactFileData) {
                    final TrampolineArtifactFileData trampolineData = (TrampolineArtifactFileData) data;
                    data = trampolineData.getDelegate();
                    if (data.getFileType().equals(ArtifactFileData.FileType.REGULAR_FILE)) {
                        if(data.getUrl()!= null) {
                            FileUtils.copyURLToFile(new URL(data.getUrl()), targetDir);
                        }
                    } else {
                        getDiscoveryFile(dataProvider, targetDir, trampolineData.getTag());
                    }
                }
            } catch (IOException e) {
                logAndThrow(e, "Failed to download artifacts to " + targetDir);
            }
        }
    }


    public static void copyArtifactTo(File targetDir, MutableArtifact artifact) {
        targetDir.mkdirs();
        final ArtifactLinkDataProvider dataProvider = artifactLinkManager.getArtifactLinkDataProvider(artifact);
        if (dataProvider instanceof FileSystemArtifactLinkDataProvider) {
            downloadAllArtifactsTo((FileSystemArtifactLinkDataProvider) dataProvider, targetDir);
        } else {
            downloadAllArtifactsTo(dataProvider, targetDir, "");
        }
    }

    private static void downloadAllArtifactsTo(ArtifactLinkDataProvider dataProvider, File targetDir, String startFrom) {
        for (ArtifactFileData data : requireNonNull(dataProvider).listObjects(startFrom)) {
            try {
                if (data instanceof TrampolineArtifactFileData) {
                    final TrampolineArtifactFileData trampolineData = (TrampolineArtifactFileData) data;
                    data = trampolineData.getDelegate();
                    if (data.getFileType().equals(ArtifactFileData.FileType.REGULAR_FILE)) {
                        final String fileName = Paths.get(data.getName()).toFile().getName();
                        FileUtils.copyURLToFile(new URL(data.getUrl()), Paths.get(targetDir.getPath(), fileName).toFile());
                    } else {
                        downloadAllArtifactsTo(dataProvider, targetDir, trampolineData.getTag());
                    }
                }
            } catch (IOException e) {
                logAndThrow(e, "Failed to download artifacts to " + targetDir);
            }
        }
    }

    private static void downloadAllArtifactsTo(FileSystemArtifactLinkDataProvider dataProvider, File targetDir) {
        ofNullable(dataProvider.getFile().listFiles())
                .map(Arrays::asList)
                .ifPresent(list -> list.forEach(file -> {
                            try {
                                if (file.isFile()) {
                                    Files.copy(file, Paths.get(targetDir.getPath(), file.getName()).toFile());
                                } else if (!file.getName().equals(".") && !file.getName().equals("..")) {
                                    FileUtils.copyDirectory(dataProvider.getFile(), targetDir);
                                }
                            } catch (IOException e) {
                                logAndThrow(e, "Failed to download artifacts to " + targetDir);
                            }
                        }
                ));
    }

    private static void logAndThrow(Exception e, String message) {
        log.error(message, e);
        throw new RuntimeException(message, e);
    }


}
