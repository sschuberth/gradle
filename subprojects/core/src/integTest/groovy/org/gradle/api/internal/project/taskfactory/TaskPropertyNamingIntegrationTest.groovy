/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package org.gradle.api.internal.project.taskfactory

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class TaskPropertyNamingIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://issues.gradle.org/browse/GRADLE-3538")
    def "names of annotated properties are used in property specs"() {
        file("input.txt").createNewFile()
        file("input-nested.txt").createNewFile()
        file("input1.txt").createNewFile()
        file("input2.txt").createNewFile()
        file("inputs").createDir()
        file("inputs/inputA.txt").createNewFile()
        file("inputs/inputB.txt").createNewFile()
        file("inputs1").createDir()
        file("inputs2").createDir()

        buildFile << """
            class MyConfig {
                @Input String inputString
                @InputFile File inputFile
                @OutputFiles Set<File> outputFiles
            }

            class MyTask extends DefaultTask {
                @Input String inputString
                @Nested MyConfig nested = new MyConfig()
                @InputFile File inputFile
                @InputDirectory File inputDirectory
                @InputFiles FileCollection inputFiles

                @OutputFile File outputFile
                @OutputFiles FileCollection outputFiles
                @OutputFiles Map<String, File> namedOutputFiles
                @OutputDirectory File outputDirectory
                @OutputDirectories FileCollection outputDirectories
                @OutputDirectories Map<String, File> namedOutputDirectories
            }

            import org.gradle.api.internal.tasks.*
            import org.gradle.api.internal.tasks.properties.*

            task myTask(type: MyTask) {
                inputString = "data"

                nested.inputString = "data"
                nested.inputFile = file("input-nested.txt")
                nested.outputFiles = [file("output-nested-1.txt"), file("output-nested-2.txt")]

                inputFile = file("input.txt")
                inputDirectory = file("inputs")
                inputFiles = files("input1.txt", "input2.txt")

                outputFile = file("output.txt")
                outputFiles = files("output1.txt", "output2.txt")
                namedOutputFiles = [one: file("output-one.txt"), two: file("output-two.txt")]
                outputDirectory = file("outputs")
                outputDirectories = files("outputs1", "outputs2")
                namedOutputDirectories = [one: file("outputs-one"), two: file("outputs-two")]

                doLast {
                    def outputFiles = []
                    def inputFiles = []
                    TaskPropertyUtils.visitProperties(project.services.get(PropertyWalker), it, new PropertyVisitor.Adapter() {
                        @Override
                        void visitInputFileProperty(TaskInputFilePropertySpec inputFileProperty) {
                            inputFiles << inputFileProperty
                        }

                        @Override
                        void visitOutputFileProperty(TaskOutputFilePropertySpec outputFileProperty) {
                            outputFiles << outputFileProperty
                        }
                    })
                    inputFiles.each { property ->
                        println "Input: \${property.propertyName} \${property.propertyFiles.files*.name.sort()}"
                    }
                    outputs.fileProperties.each { property ->
                        println "Output: \${property.propertyName} \${property.propertyFiles.files*.name.sort()}"
                    }
                }
            }
        """
        when:
        run "myTask"
        then:
        output.contains "Input: inputDirectory [inputA.txt, inputB.txt]"
        output.contains "Input: inputFile [input.txt]"
        output.contains "Input: inputFiles [input1.txt, input2.txt]"
        output.contains "Input: nested.inputFile [input-nested.txt]"
        output.contains "Output: namedOutputDirectories.one [outputs-one]"
        output.contains "Output: namedOutputDirectories.two [outputs-two]"
        output.contains "Output: namedOutputFiles.one [output-one.txt]"
        output.contains "Output: namedOutputFiles.two [output-two.txt]"
        output.contains 'Output: nested.outputFiles$1 [output-nested-1.txt]'
        output.contains 'Output: nested.outputFiles$2 [output-nested-2.txt]'
        output.contains 'Output: outputDirectories$1 [outputs1]'
        output.contains 'Output: outputDirectories$2 [outputs2]'
        output.contains "Output: outputDirectory [outputs]"
        output.contains "Output: outputFile [output.txt]"
        output.contains 'Output: outputFiles$1 [output1.txt]'
        output.contains 'Output: outputFiles$2 [output2.txt]'
    }

    def "nested input and output properties are discovered"() {
        buildFile << classesForNestedProperties()
        buildFile << """
            task test(type: TaskWithNestedObjectProperty) {           
                input = "someString"
                bean = new NestedProperty(
                    inputDir: file('input'),
                    input: 'someString',
                    outputDir: file("\$buildDir/output"),  
                    nestedBean: new AnotherNestedProperty(inputFile: file('inputFile'))
                )
            }        
            task printMetadata(type: PrintInputsAndOutputs) {
                task = test
            }
        """
        file('input').createDir()
        file('inputFile').createFile()

        expect:
        succeeds "test", "printMetadata"
        output =~ /Input property 'input'/
        output =~ /Input property 'bean\.class'/

        output =~ /Input property 'bean\.input'/
        output =~ /Input property 'bean\.nestedBean.class'/
        output =~ /Input file property 'bean\.inputDir'/
        output =~ /Input file property 'bean\.nestedBean.inputFile'/
        output =~ /Output file property 'bean\.outputDir'/
    }

    def "nested destroyables are discovered"() {
        buildFile << classesForNestedProperties()
        buildFile << """
            class MyDestroyer extends DefaultTask {
                @TaskAction void doStuff() {}
                @Nested
                Object bean
            }
            class DestroyerBean {
                @Destroys File destroyedFile
            }

            task destroy(type: MyDestroyer) {
                bean = new DestroyerBean(
                    destroyedFile: file("\$buildDir/destroyed")
                )
            }               
            task printMetadata(type: PrintInputsAndOutputs) {
                task = destroy
            }
        """

        when:
        succeeds "destroy", "printMetadata"

        then:
        output =~ /Input property 'bean\.class'/
        output =~ /Destroys: '.*destroyed'/
    }

    def "nested local state is discovered"() {
        buildFile << classesForNestedProperties()
        buildFile << """
            class TaskWithNestedLocalState extends DefaultTask {
                @TaskAction void doStuff() {}
                @Nested
                Object bean
            }
            class LocalStateBean {
                @LocalState File localStateFile
            }

            task taskWithLocalState(type: TaskWithNestedLocalState) {
                bean = new LocalStateBean(
                    localStateFile: file("\$buildDir/localState")
                )
            }               
            task printMetadata(type: PrintInputsAndOutputs) {
                task = taskWithLocalState
            }
        """

        when:
        succeeds "taskWithLocalState", "printMetadata"

        then:
        output =~ /Input property 'bean\.class'/
        output =~ /Local state: '.*localState'/
    }

    def "input properties can be overridden"() {
        buildFile << classesForNestedProperties()
        buildFile << """
            task test(type: TaskWithNestedObjectProperty) { 
                input = "someString"
                bean = new NestedProperty(
                    input: 'someString',
                )                    
                inputs.property("input", "someOtherString") 
                inputs.property("bean.input", "otherNestedString")
            }                        
            task printMetadata(type: PrintInputsAndOutputs) {
                task = test
            }
        """
        file('input').createDir()
        file('inputFile').createFile()

        when:
        succeeds "test", "printMetadata"

        then:
        output =~ /Input property 'input'/
        output =~ /Input property 'bean\.input'/

        output =~ /Input property 'bean\.class'/
        output =~ /Input property 'bean\.nestedBean\.class'/
        output =~ /Input file property 'bean\.inputDir'/
    }

    String classesForNestedProperties() {
        """
            class TaskWithNestedObjectProperty extends DefaultTask {
                @Nested
                Object bean
                @Input
                String input
                
                @TaskAction
                void doStuff() {}
            }
            
            class NestedProperty {
                @InputDirectory
                @Optional
                File inputDir
                
                @OutputDirectory
                @Optional
                File outputDir      
                        
                @Input
                String input
                @Nested
                @Optional
                Object nestedBean
                @Destroys File destroyedFile
            }                    
            class AnotherNestedProperty {
                @InputFile
                File inputFile
            }     
            
            import org.gradle.api.internal.tasks.*
            import org.gradle.api.internal.tasks.properties.*

            class PrintInputsAndOutputs extends DefaultTask {
                Task task
                @TaskAction
                void printInputsAndOutputs() {
                    TaskPropertyUtils.visitProperties(project.services.get(PropertyWalker), task, new PropertyVisitor() {
                        @Override
                        void visitInputProperty(TaskInputPropertySpec inputProperty) {
                            println "Input property '\${inputProperty.propertyName}'"
                        }

                        @Override
                        void visitInputFileProperty(TaskInputFilePropertySpec inputFileProperty) {
                            println "Input file property '\${inputFileProperty.propertyName}'"
                        }

                        @Override
                        void visitOutputFileProperty(TaskOutputFilePropertySpec outputFileProperty) {
                            println "Output file property '\${outputFileProperty.propertyName}'"
                        }

                        @Override
                        void visitDestroyableProperty(TaskDestroyablePropertySpec destroyable) {
                            println "Destroys: '\${destroyable.value.call()}'"
                        }

                        @Override
                        void visitLocalStateProperty(TaskLocalStatePropertySpec localStateProperty) {
                            println "Local state: '\${localStateProperty.value.call()}'"
                        }
                    })
                }
            }
        """
    }
}
