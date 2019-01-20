package de.axelfaust.experiment.share.adaptions;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * @author Axel Faust
 */
public class JSScriptProcessorPostProcessor implements BeanFactoryPostProcessor
{

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException
    {
        final BeanDefinition templatesJsScriptProcessorDefinition = beanFactory
                .getBeanDefinition("webframework.templates.scriptprocessor.javascript");
        templatesJsScriptProcessorDefinition.setBeanClassName(ImprovedJSScriptProcessor.class.getName());
        final BeanDefinition webScriptsJsScriptProcessorDefinition = beanFactory
                .getBeanDefinition("webframework.webscripts.scriptprocessor.javascript");
        webScriptsJsScriptProcessorDefinition.setBeanClassName(ImprovedJSScriptWithTokensProcessor.class.getName());
    }
}
