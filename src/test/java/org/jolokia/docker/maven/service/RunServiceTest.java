package org.jolokia.docker.maven.service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

import mockit.Expectations;
import mockit.Mocked;
import org.apache.commons.io.IOUtils;
import org.apache.maven.project.MavenProject;
import org.jolokia.docker.maven.access.*;
import org.jolokia.docker.maven.config.*;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;


/**
 * This test need to be refactored. In fact, testing Mojos must be setup correctly
 * at all. Blame on me that there are so few tests ...
 */
public class RunServiceTest {

    @Mocked
    private DockerAccess docker;

    @Mocked
    private MavenProject mavenProject;

    @Test
    @SuppressWarnings("unused")
    public void testCreateContainerAllConfig() throws Exception {
        /*-
         * this is really two tests in one
         *  - verify the start dockerRunner calls all the methods to build the container configs
         *  - the container configs produce the correct json when all options are specified
         *  
         * it didn't seem worth the effort to build a separate test to verify the json and then mock/verify all the calls here
         */

        VolumeConfiguration volumeConfiguration =
                new VolumeConfiguration.Builder()
                        .bind(bind())
                        .from(volumesFrom())
                        .build();
        final RunImageConfiguration runConfig =
                new RunImageConfiguration.Builder()
                        .hostname("hostname")
                        .domainname("domain.com")
                        .user("user")
                        .memory(1L)
                        .memorySwap(1L)
                        .env(env())
                        .cmd("date")
                        .entrypoint("entrypoint")
                        .extraHosts(extraHosts())
                        .workingDir("/foo")
                        .ports(ports())
                        .links(links())
                        .volumes(volumeConfiguration)
                        .dns(dns()).dnsSearch(dnsSearch())
                        .privileged(true).capAdd(capAdd())
                        .capDrop(capDrop())
                        .restartPolicy(restartPolicy())
                        .build();

        RunService runService = new RunService();
        PortMapping portMapping = runService.getPortMapping(runConfig, new Properties());

        new Expectations() {{
            docker.getContainerName((String) withNotNull());
            result = "redis";
            minTimes = 1;

        }};

        // Better than poking into the private vars would be to use createAndStart() with the mock to build up the map.
        ImageConfiguration imageConfig2 = new ImageConfiguration.Builder().alias("db").name("redis3").build();
        putToPrivateMap(runService, "containerImageNameMap", imageConfig2.getName(), "redisContainer");
        if (imageConfig2.getAlias() != null) {
            putToPrivateMap(runService,"imageAliasMap",imageConfig2.getAlias(), imageConfig2.getName());
        }
        ImageConfiguration imageConfig1 = new ImageConfiguration.Builder().alias("parent").name("parentName").build();
        putToPrivateMap(runService,"containerImageNameMap",imageConfig1.getName(), "parentContainer");
        if (imageConfig1.getAlias() != null) {
            putToPrivateMap(runService,"imageAliasMap",imageConfig1.getAlias(), imageConfig1.getName());
        }
        ImageConfiguration imageConfig = new ImageConfiguration.Builder().alias("other:ro").name("otherName").build();
        putToPrivateMap(runService,"containerImageNameMap",imageConfig.getName(), "otherContainer");
        if (imageConfig.getAlias() != null) {
            putToPrivateMap(runService,"imageAliasMap", imageConfig.getAlias(), imageConfig.getName());
        }
        ContainerCreateConfig containerConfig = runService.createContainerConfig(docker, "base", runConfig, portMapping, new Properties());

        String expectedConfig = loadFile("docker/containerCreateConfigAll.json");
        JSONAssert.assertEquals(expectedConfig, containerConfig.toJson(), true);

        ContainerHostConfig startConfig = runService.createContainerHostConfig(docker, runConfig, portMapping);
        String expectedHostConfig = loadFile("docker/containerHostConfigAll.json");
        JSONAssert.assertEquals(expectedHostConfig, startConfig.toJson(), true);
    }

    private void putToPrivateMap(RunService runService, String varName, String key, String value) throws NoSuchFieldException, IllegalAccessException {
        Field field = runService.getClass().getDeclaredField(varName);
        field.setAccessible(true);
        Map<String,String> map = (Map<String, String>) field.get(runService);
        map.put(key,value);
    }

    private List<String> bind() {
        return Collections.singletonList("/host_tmp:/container_tmp");
    }

    private List<String> capAdd() {
        return Collections.singletonList("NET_ADMIN");
    }

    private List<String> capDrop() {
        return Collections.singletonList("MKNOD");
    }

    private List<String> dns() {
        return Collections.singletonList("8.8.8.8");
    }

    private List<String> dnsSearch() {
        return Collections.singletonList("domain.com");
    }

    private Map<String, String> env() {
        Map<String, String> env = new HashMap<>();
        env.put("foo", "bar");

        return env;
    }

    private List<String> extraHosts() {
        return Collections.singletonList("localhost:127.0.0.1");
    }

    private List<String> links() {
        return Collections.singletonList("redis3:redis");
    }

    private String loadFile(String fileName) throws IOException {
        return IOUtils.toString(getClass().getClassLoader().getResource(fileName));
    }

    private List<String> ports() {
        return Collections.singletonList("0.0.0.0:11022:22");
    }

    private RestartPolicy restartPolicy() {
        return new RestartPolicy.Builder().name("on-failure").retry(1).build();
    }

    private List<String> volumesFrom() {
        return Arrays.asList("parent", "other:ro");
    }
}

