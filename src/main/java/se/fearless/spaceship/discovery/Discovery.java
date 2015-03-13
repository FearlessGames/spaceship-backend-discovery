package se.fearless.spaceship.discovery;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.ServicePort;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.server.EurekaWriteServer;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.transport.EurekaTransports;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.file.ClassPathFileRequestHandler;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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
			EurekaClient client = Eureka.newClientBuilder(ServerResolvers.from(new ServerResolver.Server("localhost", 2222))).
					withCodec(EurekaTransports.Codec.Json).build();

			startEurekaDashboard(2228, client);
			eurekaWriteServer.waitTillShutdown();
		} catch (Exception e) {
			logger.error("Eureka problem!", e);
		}
	}

	public static class StaticFileHandler extends ClassPathFileRequestHandler {
		public StaticFileHandler() {
			super(".");
		}
	}

	private static void startEurekaDashboard(final int port, EurekaClient client) {

		final ObjectMapper objectMapper = new ObjectMapper();
		final ObjectWriter objectWriter = objectMapper.writerWithType(Map.class);

		final StaticFileHandler staticFileHandler = new StaticFileHandler();

		RxNetty.createHttpServer(port, (request, response) -> {
			if (request.getUri().startsWith("/dashboard")) {
				return staticFileHandler.handle(request, response);
			} else if (request.getUri().startsWith("/data")) {
				response.getHeaders().set(HttpHeaders.Names.CONTENT_TYPE, "text/event-stream");
				response.getHeaders().set(HttpHeaders.Names.CACHE_CONTROL, "no-cache");
				return client.forInterest(Interests.forFullRegistry())
						.flatMap(notification -> {
							ByteBuf data = response.getAllocator().buffer();
							data.writeBytes("data: ".getBytes());
							Map<String, String> dataAttributes = new HashMap<>();
							dataAttributes.put("type", notification.getKind().toString());
							dataAttributes.put("instance-id", notification.getData().getId());
							dataAttributes.put("vip", notification.getData().getVipAddress());
							if (notification.getData().getStatus() != null) {
								dataAttributes.put("status", notification.getData().getStatus().name());
							}
							HashSet<ServicePort> servicePorts = notification.getData().getPorts();
							int port1 = servicePorts.iterator().next().getPort();
							dataAttributes.put("port", String.valueOf(port1));
							String jsonData = null;
							try {
								jsonData = objectWriter.writeValueAsString(dataAttributes);
							} catch (IOException e) {
								e.printStackTrace();
							}
							data.writeBytes(jsonData.getBytes());
							data.writeBytes("\n\n".getBytes());
							return response.writeBytesAndFlush(data);
						});
			} else {
				response.setStatus(HttpResponseStatus.NOT_FOUND);
				return Observable.empty();
			}
		}).start();
	}
}
