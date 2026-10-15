package guessmarket.server;

import guessmarket.engine.service.GuessMarketEngine;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Creates the single engine instance when the web application starts and
 * parks it in the servlet context, so every request works against the same
 * state. Nothing is persisted: when Tomcat stops, the state is gone, exactly
 * as the exercise requires.
 */
@WebListener
public class EngineContextListener implements ServletContextListener {

    public static final String ENGINE_ATTR = "guessmarket.engine";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().setAttribute(ENGINE_ATTR, new GuessMarketEngine());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        sce.getServletContext().removeAttribute(ENGINE_ATTR);
    }

    public static GuessMarketEngine get(jakarta.servlet.ServletContext ctx) {
        GuessMarketEngine e = (GuessMarketEngine) ctx.getAttribute(ENGINE_ATTR);
        if (e == null) {                    // defensive: listener somehow did not run
            synchronized (EngineContextListener.class) {
                e = (GuessMarketEngine) ctx.getAttribute(ENGINE_ATTR);
                if (e == null) {
                    e = new GuessMarketEngine();
                    ctx.setAttribute(ENGINE_ATTR, e);
                }
            }
        }
        return e;
    }
}
