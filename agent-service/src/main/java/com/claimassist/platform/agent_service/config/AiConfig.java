package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.ai.ChatClientAdapter;
import com.claimassist.platform.agent_service.ai.OptionalChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

@Configuration
@ConditionalOnClass(name = "org.springframework.ai.chat.client.ChatClient")
public class AiConfig {

    /**
     * Provide an OptionalChatClient adapter bean if spring-ai is present. The
     * adapter wraps the real ChatClient reflectively so the project does not
     * need a compile-time dependency on spring-ai for local startup.
     */
    @Bean
    public OptionalChatClient optionalChatClient(ApplicationContext ctx) {
        try {
            Class<?> builderClass = Class.forName("org.springframework.ai.chat.client.ChatClient$Builder");
            Object builder = ctx.getBean(builderClass);
            Class<?> advisorClass = Class.forName("org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor");
            Object advisor = advisorClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method defaultAdvisors = builderClass.getMethod("defaultAdvisors", Object[].class);
            defaultAdvisors.invoke(builder, new Object[] { new Object[] { advisor } });
            java.lang.reflect.Method build = builderClass.getMethod("build");
            Object realClient = build.invoke(builder);
            return new ChatClientAdapter(realClient);
        } catch (ClassNotFoundException e) {
            // spring-ai not on the classpath -> do not create adapter bean
            return null;
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException ex) {
            // builder bean not present -> skip
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
