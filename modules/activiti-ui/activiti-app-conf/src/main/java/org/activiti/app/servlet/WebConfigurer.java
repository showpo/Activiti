/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.app.servlet;

import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;

import org.activiti.app.conf.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Configuration of web application with Servlet 3.0 APIs.
 */
public class WebConfigurer implements ServletContextListener {
	
    private final Logger log = LoggerFactory.getLogger(WebConfigurer.class);

    public AnnotationConfigWebApplicationContext context;
    
    public void setContext(AnnotationConfigWebApplicationContext context) {
        this.context = context;
    }
    /**
     * 初始化方法
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.debug("Configuring Spring root application context");

        ServletContext servletContext = sce.getServletContext();
        // 定义为根容器
        AnnotationConfigWebApplicationContext rootContext = null;
        // 当上下文容器为空，则重新构建容器
        if (context == null) {
            rootContext = new AnnotationConfigWebApplicationContext();
            // 注册 ApplicationConfiguration
            rootContext.register(ApplicationConfiguration.class);
            
            if (rootContext.getServletContext() == null) {
              rootContext.setServletContext(servletContext);
            }
            
            rootContext.refresh();
            context = rootContext;
            
        } else {
            rootContext = context;
            if (rootContext.getServletContext() == null) {
                // 将 spring 容器和 servlet 容器做了双向绑定【将servlet 容器作为属性放到了 spring 中】
              rootContext.setServletContext(servletContext);
            }
        }
        // 将 spring 容器和 servlet 容器做了双向绑定【将 spring 容器作为属性放到了 servlet 中】
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, rootContext);

        EnumSet<DispatcherType> disps = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC);
        // 初始化 spring 和 Spring MVC
        initSpring(servletContext, rootContext);
        // 初始化 spring 安全相关内容
        initSpringSecurity(servletContext, disps);

        log.debug("Web application fully configured");
    }

    /**
     * 初始化 spring 和 Spring MVC
     * Initializes Spring and Spring MVC.
     */
    private void initSpring(ServletContext servletContext, AnnotationConfigWebApplicationContext rootContext) {
        log.debug("Configuring Spring Web application context");
        AnnotationConfigWebApplicationContext appDispatcherServletConfiguration = new AnnotationConfigWebApplicationContext();
        // 设置根上下文
        appDispatcherServletConfiguration.setParent(rootContext);
        appDispatcherServletConfiguration.register(AppDispatcherServletConfiguration.class);

        // 注册 Spring MVC Servlet
        log.debug("Registering Spring MVC Servlet");
        ServletRegistration.Dynamic appDispatcherServlet = servletContext.addServlet("appDispatcher", 
                new DispatcherServlet(appDispatcherServletConfiguration));
        appDispatcherServlet.addMapping("/app/*");
        appDispatcherServlet.setLoadOnStartup(1);
        appDispatcherServlet.setAsyncSupported(true);

        // 注册 Activiti 公共 rest api
        log.debug("Registering Activiti public REST API");
        AnnotationConfigWebApplicationContext apiDispatcherServletConfiguration = new AnnotationConfigWebApplicationContext();
        apiDispatcherServletConfiguration.setParent(rootContext);
        apiDispatcherServletConfiguration.register(ApiDispatcherServletConfiguration.class);

        ServletRegistration.Dynamic apiDispatcherServlet = servletContext.addServlet("apiDispatcher",
                new DispatcherServlet(apiDispatcherServletConfiguration));
        apiDispatcherServlet.addMapping("/api/*");
        apiDispatcherServlet.setLoadOnStartup(1);
        apiDispatcherServlet.setAsyncSupported(true);
    }

    /**
     * Initializes Spring Security.
     */
    private void initSpringSecurity(ServletContext servletContext, EnumSet<DispatcherType> disps) {
        log.debug("Registering Spring Security Filter");
        FilterRegistration.Dynamic springSecurityFilter = servletContext.addFilter("springSecurityFilterChain", new DelegatingFilterProxy());

        springSecurityFilter.addMappingForUrlPatterns(disps, false, "/*");
        springSecurityFilter.setAsyncSupported(true);
    }


    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("Destroying Web application");
        WebApplicationContext ac = WebApplicationContextUtils.getRequiredWebApplicationContext(sce.getServletContext());
        AnnotationConfigWebApplicationContext gwac = (AnnotationConfigWebApplicationContext) ac;
        gwac.close();
        log.debug("Web application destroyed");
    }
}
