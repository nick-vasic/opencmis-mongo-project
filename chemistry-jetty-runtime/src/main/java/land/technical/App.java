package land.technical;

import java.lang.management.ManagementFactory;

import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * Run Apache Chemistry Development Environment
 *
 */
public class App {

	public static void main(String[] args) throws Exception {
		Server server = new Server(8080);
		MBeanContainer mbContainer = new MBeanContainer(
				ManagementFactory.getPlatformMBeanServer());
		server.addBean(mbContainer);
		WebAppContext webapp = new WebAppContext();
		webapp.setContextPath("/");
		webapp.setWar("../../jetty-distribution/target/distribution/demo-base/webapps/test.war");
		server.setHandler(webapp);
		HashLoginService loginService = new HashLoginService();
		loginService.setName("Test Realm");
		loginService.setConfig("src/test/resources/realm.properties");
		server.addBean(loginService);
		server.start();
		server.join();

	}
}
