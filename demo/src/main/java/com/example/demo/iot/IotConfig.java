package com.example.demo.iot;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.region.Region;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IotConfig {

    @Value("${huawei.iot.ak}")
    private String ak;

    @Value("${huawei.iot.sk}")
    private String sk;

    @Value("${huawei.iot.regionId}")
    private String regionId;

    @Value("${huawei.iot.endpoint}")
    private String endpoint;

    @Value("${huawei.iot.projectId}")
    private String projectId;

    @Bean
    public IoTDAClient ioTDAClient() {
        BasicCredentials credentials = new BasicCredentials()
                .withAk(ak)
                .withSk(sk)
                .withProjectId(projectId)
                // IoTDA关键！必须加衍生谓词，不加直接401 IOTDA.000002
                .withDerivedPredicate(BasicCredentials.DEFAULT_DERIVED_PREDICATE);

        Region region = new Region(regionId, endpoint);
        return IoTDAClient.newBuilder()
                .withCredential(credentials)
                .withRegion(region)
                .build();
    }
}
