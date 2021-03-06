//======================================
//  Kyle Russell
//  AUT University 2016
//  Highly Secured Systems A2
//======================================

package com.kyleruss.safesms.server.web.app;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ContextListener implements ServletContextListener
{
    @Override
    public void contextInitialized(ServletContextEvent sce) 
    {
        ServerKeyManager.getInstance().init();
        UserManager.getInstance().init();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {}
}
