package io.symphonia.lambda.metrics;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Execute(phase = LifecyclePhase.COMPILE)
@Mojo(name = "publish")
public class PublishMetricFiltersMojo extends AbstractMetricFiltersMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "dryRun", defaultValue = "false", required = true)
    private boolean dryRun;

    @Parameter(required = false)
    private Map<String, String> filterPatternMap = COMPLETE_FILTER_PATTERN_MAP;

    @Parameter(required = false)
    private Map<String, List<String>> metricValueMap = REDUCED_METRIC_VALUE_MAP;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        checkConfiguration();

        MetricFilterPublisher publisher =
                dryRun ? new DryRunMetricFilterPublisher(getLog()) : new CloudwatchMetricFilterPublisher(getLog());

        // Gets a map of fully-namespaced metric names -> annotated fields in classes that extend LambdaMetricSet
        Map<String, Field> metricFields = new HashMap<>();
        try {
            metricFields.putAll(new MetricsFinder(project).find());
        } catch (DependencyResolutionRequiredException | MalformedURLException e) {
            throw new MojoExecutionException("Could not scan classpath for metric fields", e);
        }

        if (!metricFields.isEmpty()) {
            getLog().info(String.format("Found [%d] metric fields in classpath.", metricFields.size()));

            List<MetricFilter> metricFilters = getMetricFilters(metricFields);

            getLog().info(String.format("Published [%d] metric filters.",
                    publisher.publishMetricFilters(metricFilters)));
        } else {
            getLog().warn("Did not find any metric fields in classpath.");
        }

    }

    private void checkConfiguration() throws MojoFailureException {
        if (!filterPatternMap.keySet().containsAll(metricValueMap.keySet())
                || !metricValueMap.keySet().containsAll(filterPatternMap.keySet())) {
            throw new MojoFailureException("filterPatternMap and metricValueMap are inconsistent.");
        }
    }

}
