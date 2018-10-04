package de.axelfaust.experiment.share.jetty;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * @author Axel Faust
 */
public class UrlConfigSourcePostProcessor implements BeanFactoryPostProcessor
{

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException
    {
        final BeanDefinition configSourceDefinition = beanFactory.getBeanDefinition("webframework.configsource");
        configSourceDefinition.setBeanClassName(ImproveWebappHandlingUrlConfigSource.class.getName());
    }

}
