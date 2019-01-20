package de.axelfaust.experiment.share.adaptions;

import java.util.List;

import org.alfresco.util.PropertyCheck;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.extensions.webscripts.ClassPathStore;

/**
 *
 * @author Axel Faust
 */
public class ClassPathStorePostProcessor implements BeanFactoryPostProcessor, InitializingBean
{

    protected List<String> beanNames;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "beanNames", this.beanNames);
    }

    /**
     * @param beanNames
     *            the beanNames to set
     */
    public void setBeanNames(final List<String> beanNames)
    {
        this.beanNames = beanNames;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException
    {
        for (final String beanName : this.beanNames)
        {
            if (beanFactory.containsBean(beanName))
            {
                final BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
                final String beanClassName = beanDefinition.getBeanClassName();
                if (beanClassName == null || beanClassName.equals(ClassPathStore.class.getName()))
                {
                    beanDefinition.setBeanClassName(FatJarAwareClassPathStore.class.getName());
                }
            }
        }
    }
}
