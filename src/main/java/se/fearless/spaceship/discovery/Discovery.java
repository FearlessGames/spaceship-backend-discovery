package se.fearless.spaceship.discovery;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.server.EurekaWriteServer;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.transport.EurekaTransports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Discovery {

	public static void main(String[] args) {
		Logger logger = LoggerFactory.getLogger(Discovery.class);

		WriteServerConfig.WriteServerConfigBuilder builder = new WriteServerConfig.WriteServerConfigBuilder();
		builder.withDiscoveryPort(2222)
				.withRegistrationPort(2223)
				.withWebAdminPort(2224)
//				.withReplicationPort(2225)
				.withCodec(EurekaTransports.Codec.Json)

				.withDataCenterType(LocalDataCenterInfo.DataCenterType.Basic);
		EurekaWriteServer eurekaWriteServer = new EurekaWriteServer(builder.build());

		try {
			eurekaWriteServer.start();
			logger.info("Eureka server started");
			eurekaWriteServer.waitTillShutdown();
		} catch (Exception e) {
			logger.error("Eureka problem!", e);
		}
	}
}
