/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.knittingboar.sgd;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.io.Writable;
import org.apache.mahout.classifier.sgd.AbstractOnlineLogisticRegression;
import org.apache.mahout.classifier.sgd.DefaultGradient;
import org.apache.mahout.classifier.sgd.Gradient;
import org.apache.mahout.classifier.sgd.PolymorphicWritable;
import org.apache.mahout.classifier.sgd.PriorFunction;
import org.apache.mahout.math.DenseMatrix;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.MatrixWritable;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.VectorWritable;

import com.cloudera.knittingboar.utils.Utils;

/**
 * Parallel Online Logisitc Regression
 * 
 * Based loosely on Mahout's :
 * 
 * http://svn.apache.org/repos/asf/mahout/trunk/core/src/main/java/org/apache/
 * mahout/classifier/sgd/OnlineLogisticRegression.java
 * 
 * 
 * @author jpatterson
 * 
 */
public class ParallelOnlineLogisticRegression extends
    AbstractOnlineLogisticRegression implements Writable {
  public static final int WRITABLE_VERSION = 1;
  
  // these next two control decayFactor^steps exponential type of annealing
  // learning rate and decay factor
  private double learningRate = 1;
  private double decayFactor = 1 - 1.0e-3;
  
  // these next two control 1/steps^forget type annealing
  private int stepOffset = 10;
  // -1 equals even weighting of all examples, 0 means only use exponential
  // annealing
  private double forgettingExponent = -0.5;
  
  // controls how per term annealing works
  private int perTermAnnealingOffset = 20;
  
  // had to add this because its private in the base class
  private Gradient default_gradient = new DefaultGradient();
  
  // ####### This is NEW ######################
  // that is (numCategories-1) x numFeatures
  //protected MultinomialLogisticRegressionParameterVectors gamma; // this is the saved updated gradient we merge
                                  // at the super step
  
  public ParallelOnlineLogisticRegression() {
  // private constructor available for serialization, but not normal use
    
  }
  
  /**
   * Main constructor
   * 
   * 
   * 
   * @param numCategories
   * @param numFeatures
   * @param prior
   */
  public ParallelOnlineLogisticRegression(int numCategories, int numFeatures,
      PriorFunction prior) {
    this.numCategories = numCategories;
    this.prior = prior;
    
    updateSteps = new DenseVector(numFeatures);
    updateCounts = new DenseVector(numFeatures).assign(perTermAnnealingOffset);
    beta = new DenseMatrix(numCategories - 1, numFeatures);
    
    // brand new factor for parallelization
//    this.gamma = new MultinomialLogisticRegressionParameterVectors(numCategories, numFeatures);
  }
  
  /**
   * Chainable configuration option.
   * 
   * @param alpha
   *          New value of decayFactor, the exponential decay rate for the
   *          learning rate.
   * @return This, so other configurations can be chained.
   */
  public ParallelOnlineLogisticRegression alpha(double alpha) {
    this.decayFactor = alpha;
    return this;
  }
  
  @Override
  public ParallelOnlineLogisticRegression lambda(double lambda) {
    // we only over-ride this to provide a more restrictive return type
    super.lambda(lambda);
    return this;
  }
  
  /**
   * Chainable configuration option.
   * 
   * @param learningRate
   *          New value of initial learning rate.
   * @return This, so other configurations can be chained.
   */
  public ParallelOnlineLogisticRegression learningRate(double learningRate) {
    this.learningRate = learningRate;
    return this;
  }
  
  public ParallelOnlineLogisticRegression stepOffset(int stepOffset) {
    this.stepOffset = stepOffset;
    return this;
  }
  
  public ParallelOnlineLogisticRegression decayExponent(double decayExponent) {
    if (decayExponent > 0) {
      decayExponent = -decayExponent;
    }
    this.forgettingExponent = decayExponent;
    return this;
  }
  
  @Override
  public double perTermLearningRate(int j) {
    return Math.sqrt(perTermAnnealingOffset / updateCounts.get(j));
  }
  
  @Override
  public double currentLearningRate() {
    return learningRate * Math.pow(decayFactor, getStep())
        * Math.pow(getStep() + stepOffset, forgettingExponent);
  }
  
  public void copyFrom(ParallelOnlineLogisticRegression other) {
    super.copyFrom(other);
    learningRate = other.learningRate;
    decayFactor = other.decayFactor;
    
    stepOffset = other.stepOffset;
    forgettingExponent = other.forgettingExponent;
    
    perTermAnnealingOffset = other.perTermAnnealingOffset;
  }
  
  public ParallelOnlineLogisticRegression copy() {
    close();
    ParallelOnlineLogisticRegression r = new ParallelOnlineLogisticRegression(
        numCategories(), numFeatures(), prior);
    r.copyFrom(this);
    return r;
  }
  
  /**
   * TODO - add something in to write the gamma to the output stream -- do we
   * need to save gamma?
   */
  @Override
  public void write(DataOutput out) throws IOException {
    out.writeInt(WRITABLE_VERSION);
    out.writeDouble(learningRate);
    out.writeDouble(decayFactor);
    out.writeInt(stepOffset);
    out.writeInt(step);
    out.writeDouble(forgettingExponent);
    out.writeInt(perTermAnnealingOffset);
    out.writeInt(numCategories);
    MatrixWritable.writeMatrix(out, beta);
    PolymorphicWritable.write(out, prior);
    VectorWritable.writeVector(out, updateCounts);
    VectorWritable.writeVector(out, updateSteps);
    
  }
  
  @Override
  public void readFields(DataInput in) throws IOException {
    int version = in.readInt();
    if (version == WRITABLE_VERSION) {
      learningRate = in.readDouble();
      decayFactor = in.readDouble();
      stepOffset = in.readInt();
      step = in.readInt();
      forgettingExponent = in.readDouble();
      perTermAnnealingOffset = in.readInt();
      numCategories = in.readInt();
      beta = MatrixWritable.readMatrix(in);
      prior = PolymorphicWritable.read(in, PriorFunction.class);
      
      updateCounts = VectorWritable.readVector(in);
      updateSteps = VectorWritable.readVector(in);
    } else {
      throw new IOException("Incorrect object version, wanted "
          + WRITABLE_VERSION + " got " + version);
    }
    
  }
  
  /**
   * Custom training for POLR based around accumulating gradient to send to the
   * master process
   * 
   * 
   */
  @Override
  public void train(long trackingKey, String groupKey, int actual,
      Vector instance) {
    unseal();
    double learningRate = currentLearningRate();
    
    // push coefficients back to zero based on the prior
    regularize(instance);
    
    // basically this only gets the results for each classification
    // update each row of coefficients according to result
    Vector gradient = this.default_gradient.apply(groupKey, actual, instance,
        this);
    for (int i = 0; i < numCategories - 1; i++) {
      
      double gradientBase = gradient.get(i);
      
      // we're only going to look at the non-zero elements of the vector
      // then we apply the gradientBase to the resulting element.
      Iterator<Vector.Element> nonZeros = instance.iterateNonZero();
      
      while (nonZeros.hasNext()) {
        Vector.Element updateLocation = nonZeros.next();
        int j = updateLocation.index();
        
        double gradient_to_add = gradientBase * learningRate
            * perTermLearningRate(j) * instance.get(j);
        
        // double old_beta = beta.getQuick(i, j);
        
        double newValue = beta.getQuick(i, j) + gradientBase * learningRate
            * perTermLearningRate(j) * instance.get(j);
        beta.setQuick(i, j, newValue);
        
        // now update gamma --- we only want the gradient since the last time
/*        
        double old_gamma = gamma.getCell(i, j);
        double new_gamma = old_gamma + gradient_to_add; // gradientBase *
                                                        // learningRate *
                                                        // perTermLearningRate(j)
                                                        // * instance.get(j);
        
        gamma.setCell(i, j, new_gamma);
  */      
      }
    }
    
    // remember that these elements got updated
    Iterator<Vector.Element> i = instance.iterateNonZero();
    while (i.hasNext()) {
      Vector.Element element = i.next();
      int j = element.index();
      updateSteps.setQuick(j, getStep());
      updateCounts.setQuick(j, updateCounts.getQuick(j) + 1);
    }
    nextStep();
    
  }
  
  /**
   * get the current parameter vector
   * 
   * @return Matrix
   */
  public Matrix noReallyGetBeta() {
    
    return this.beta;
    
  }
  
  public void SetBeta(Matrix beta_mstr_cpy) {
    
    this.beta = beta_mstr_cpy.clone();
    
  }
  
  /**
   * Spit out the current values for Gamma (gradient buffer since last flush)
   * and Beta (parameter vector)
   * 
   */
  public void Debug_PrintGamma() {
    
    System.out.println("# Debug_PrintGamma > Beta: ");
    Utils.PrintVectorSectionNonZero(this.noReallyGetBeta().viewRow(0), 10);
    
  }
  
  /**
   * Reset all values in Gamma (gradient buffer) back to zero
   * 
   */
/*  public void FlushGamma() {
    
    this.gamma.Reset();
    
  }
  
  public MultinomialLogisticRegressionParameterVectors getGamma() {
    return this.gamma;
  }
*/  
}
