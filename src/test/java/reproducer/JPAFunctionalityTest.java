package reproducer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;

/**
 * Test various JPA operations running in Shamrock
 */
public class JPAFunctionalityTest {

    @Test
    public void testJPAFunctionalityFromServlet() throws Exception {

        DeploymentInfo d = new DeploymentInfo();
        d.setDeploymentName("test");
        d.setContextPath("/");
        d.setClassLoader(getClass().getClassLoader());
        d.addServlet(new ServletInfo("test", JPAFunctionalityTestEndpoint.class)
                .addMapping("/jpa/testfunctionality"));
        ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentManager manager = container.addDeployment(d);
        manager.deploy();

        Undertow.builder()
                .setHandler(manager.start())
                .addHttpListener(8080, "localhost")
                .build()
                .start();


        for (int i = 0; i < 1000; ++i) {
            try {
                assertEquals("OK", URLTester.relative("jpa/testfunctionality").invokeURL().asString());
            } catch (Throwable e) {
                throw new RuntimeException("Failed on " + i, e);
            }
        }
    }

}
