package land.technical;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import org.apache.jasper.servlet.JspServlet;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;


/**
 * Run Apache Chemistry Development Environment
 *
 */
public class App {

	public static void main(String[] args) throws Exception {
		Server server = new Server(8081);
		MBeanContainer mbContainer = new MBeanContainer(
				ManagementFactory.getPlatformMBeanServer());
		server.addBean(mbContainer);
		WebAppContext webapp = new WebAppContext();
		webapp.setContextPath("/cmis/mongodb");
		webapp.setWar("/Users/nickvasic/git/opencmis-mongo-project/chemistry-opencmis-server-mongodb/target/chemistry-opencmis-server-mongodb-1.0.0-SNAPSHOT.war");
		server.setHandler(webapp);
		
        initJspContext(webapp);

        
		HashLoginService loginService = new HashLoginService();
		loginService.setName("Test Realm");
		loginService.setConfig("src/test/resources/realm.properties");
		server.addBean(loginService);
		server.start();
		server.join();

	}


	private static void initJspContext(WebAppContext webapp) throws IOException {
		// Establish Scratch directory for the servlet context (used by JSP compilation)
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File scratchDir = new File(tempDir.toString(),"embedded-jetty-jsp");

        if (!scratchDir.exists()) {
            if (!scratchDir.mkdirs()) {
                throw new IOException("Unable to create scratch directory: " + scratchDir);
            }
        }

        webapp.setAttribute("javax.servlet.context.tempdir",scratchDir);
        webapp.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
        

        //Ensure the jsp engine is initialized correctly
        JettyJasperInitializer sci = new JettyJasperInitializer();
        ServletContainerInitializersStarter sciStarter = new ServletContainerInitializersStarter(webapp);
        ContainerInitializer initializer = new ContainerInitializer(sci, null);
        List<ContainerInitializer> initializers = new ArrayList<ContainerInitializer>();
        initializers.add(initializer);

        webapp.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
        webapp.addBean(sciStarter, true);

        // Add JSP Servlet (must be named "jsp")
        ServletHolder holderJsp = new ServletHolder("jsp",JspServlet.class);
        holderJsp.setInitOrder(0);
        holderJsp.setInitParameter("logVerbosityLevel","DEBUG");
        holderJsp.setInitParameter("fork","false");
        holderJsp.setInitParameter("xpoweredBy","false");
        holderJsp.setInitParameter("compilerTargetVM","1.7");
        holderJsp.setInitParameter("compilerSourceVM","1.7");
        holderJsp.setInitParameter("keepgenerated","true");
        webapp.addServlet(holderJsp,"*.jsp");
        //context.addServlet(holderJsp,"*.jspf");
        //context.addServlet(holderJsp,"*.jspx");
	}
}
