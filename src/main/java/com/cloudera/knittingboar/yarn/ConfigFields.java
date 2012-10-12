package com.cloudera.knittingboar.yarn;

import java.util.Properties;

public class ConfigFields {
  public static final String DEFAULT_CONFIG_FILE = "app.properties";
  public static final String APP_CONFIG_FILE = "knittingboar.app.properties";
  
  public static final String JAR_PATH = "knittingboar.jar.path";

  public static final String APP_NAME = "app.name";
  
  public static final String APP_JAR_PATH = "app.jar.path";
  public static final String APP_LIB_PATH = "app.lib.jar.path";

  public static final String APP_INPUT_PATH = "app.input.path";
  public static final String APP_OUTPUT_PATH = "app.output.path";

  public static final String APP_BATCH_SIZE = "app.batch.size";
  public static final String APP_ITERATION_COUNT = "app.iteration.count";

  public static final String YARN_MEMORY = "yarn.memory";

  public static final String YARN_MASTER = "yarn.master.main";
  public static final String YARN_MASTER_ARGS = "yarn.master.args";
  
  public static final String YARN_WORKER = "yarn.worker.main";
  public static final String YARN_WORKER_ARGS = "yarn.worker.args";
  
  public static void validateConfig(Properties props) throws IllegalArgumentException {
    StringBuffer errors = new StringBuffer();
    String missing = " is missing\n";
    
    if (!props.containsKey(JAR_PATH))
      errors.append("Knitting Boar JAR path [" + JAR_PATH + "]").append(missing);
    
    if (!props.containsKey(APP_JAR_PATH))
      errors.append("Application JAR path [" + APP_JAR_PATH + "]").append(missing);
    
    if (!props.containsKey(YARN_MEMORY))
      errors.append("YARN memory [" + YARN_MEMORY + "]").append(missing);
    
    if (!props.containsKey(YARN_MASTER))
      errors.append("YARN master class [" + YARN_MASTER + "]").append(missing);

    if (!props.containsKey(YARN_WORKER))
      errors.append("YARN worker class [" + YARN_WORKER + "]").append(missing);
    
    if (errors.length() > 0)
      throw new IllegalArgumentException(errors.toString());
  }
  
  
}