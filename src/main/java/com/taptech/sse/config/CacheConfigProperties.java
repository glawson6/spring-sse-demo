package com.taptech.sse.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@Data
@ConfigurationProperties(prefix = "cache")
public class CacheConfigProperties {

	private String host;
	private Integer port;
	private String password;
	private String username;
	private Integer timeout = 5000;
	private String name;
	private String expiringCacheEntryName;
	private boolean useSSl = true;

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("CacheConfigProperties{");
		sb.append("host='").append(host).append('\'');
		sb.append(", port=").append(port);
		sb.append(", password='").append("*********").append('\'');
		sb.append(", username='").append(username).append('\'');
		sb.append(", timeout=").append(timeout);
		sb.append(", name='").append(name).append('\'');
		sb.append(", expiringCacheEntryName='").append(expiringCacheEntryName).append('\'');
		sb.append('}');
		return sb.toString();
	}

}
