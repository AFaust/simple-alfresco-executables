package de.axelfaust.experiment.share.adaptions;

import org.springframework.extensions.surf.processor.JSScriptWithTokensProcessor;

/**
 * @author Axel Faust
 */
public class ImprovedJSScriptWithTokensProcessor extends JSScriptWithTokensProcessor
{

    private static final String PATH_CLASSPATH = "classpath:";

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String loadScriptResource(final String resource)
    {
        String effectiveResource = resource;
        if (resource.startsWith(PATH_CLASSPATH))
        {
            // strip leading slashes which cause issues when trying to resolve files contained in JARs
            String scriptClasspath = resource.substring(PATH_CLASSPATH.length());
            if (scriptClasspath.startsWith("/"))
            {
                scriptClasspath = scriptClasspath.substring(1);
            }
            effectiveResource = PATH_CLASSPATH + scriptClasspath;
        }
        return super.loadScriptResource(effectiveResource);
    }
}
