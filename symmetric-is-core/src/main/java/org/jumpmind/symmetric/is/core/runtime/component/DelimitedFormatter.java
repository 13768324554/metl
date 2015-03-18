package org.jumpmind.symmetric.is.core.runtime.component;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jumpmind.exception.IoException;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.csv.CsvWriter;
import org.jumpmind.symmetric.is.core.model.ComponentVersionAttributeSetting;
import org.jumpmind.symmetric.is.core.model.SettingDefinition;
import org.jumpmind.symmetric.is.core.model.SettingDefinition.Type;
import org.jumpmind.symmetric.is.core.runtime.EntityData;
import org.jumpmind.symmetric.is.core.runtime.IExecutionTracker;
import org.jumpmind.symmetric.is.core.runtime.Message;
import org.jumpmind.symmetric.is.core.runtime.flow.IMessageTarget;
import org.jumpmind.symmetric.is.core.runtime.resource.IResourceFactory;

@ComponentDefinition(typeName = DelimitedFormatter.TYPE, category = ComponentCategory.PROCESSOR, iconImage="format.png",
supports = { ComponentSupports.INPUT_MESSAGE, ComponentSupports.INPUT_MODEL, ComponentSupports.OUTPUT_MESSAGE })

public class DelimitedFormatter extends AbstractComponent {

    public static final String TYPE = "Delimited Formatter";

    @SettingDefinition(order = 10, required = true, type = Type.STRING, label = "Delimiter")
    public final static String DELIMITED_FORMATTER_DELIMITER = "delimited.formatter.delimiter";
    
    @SettingDefinition(order = 20, required = true, type = Type.STRING, label = "Quote Character")
    public final static String DELIMITED_FORMATTER_QUOTE_CHARACTER = "delimited.formatter.quote.character";
    
    @SettingDefinition(order = 30, required = true, type = Type.INTEGER, label = "Ordinal")
    public final static String DELIMITED_FORMATTER_ATTRIBUTE_ORDINAL = "delimited.formatter.attribute.ordinal";
    
    @SettingDefinition(order = 40, required = false, type = Type.STRING, label = "FormatFunction")
    public final static String DELIMITED_FORMATTER_ATTRIBUTE_FORMAT_FUNCTION = "delimited.formatter.attribute.format.function";
    
    /* settings */
    String formatType;
    String delimiter;
    String quoteCharacter;

    /* other vars */
    TypedProperties properties;
    List<AttributeOrdinal> attributes = new ArrayList<AttributeOrdinal>();
    

    @Override
    public void start(IExecutionTracker executionTracker, IResourceFactory resourceFactory) {
        super.start(executionTracker, resourceFactory);
        applySettings();
    }

    @Override
    public void handle(String executionId, Message inputMessage, IMessageTarget messageTarget) {
        
        componentStatistics.incrementInboundMessages();
        ArrayList<EntityData> inputRows = inputMessage.getPayload();

        Message outputMessage = new Message(flowStep.getId());
        ArrayList<String> outputPayload = new ArrayList<String>(); 
        
        String outputRec;
        for (EntityData inputRow : inputRows) {
            outputRec = processInputRow(inputRow);
            outputPayload.add(outputRec);
        } 
        outputMessage.setPayload(outputPayload);
        messageTarget.put(outputMessage);
    }
    
    private String processInputRow(EntityData inputRow) {
   
        Writer writer = new StringWriter();
        CsvWriter csvWriter = new CsvWriter(writer, delimiter.charAt(0));
        if (!StringUtils.isEmpty(quoteCharacter)) {
            csvWriter.setTextQualifier(quoteCharacter.charAt(0));
        }        
        try {
            for (AttributeOrdinal attribute : attributes) {
                csvWriter.write(inputRow.get(attribute.getAttributeId()).toString());
            }
            csvWriter.endRecord();
        } catch (IOException e) {
            throw new IoException("Error writing to stream for formatted output. " + e.getMessage());
        }
        return writer.toString();
    }
    
    private void applySettings() {
        properties = flowStep.getComponentVersion().toTypedProperties(this, false);
        delimiter = properties.get(DELIMITED_FORMATTER_DELIMITER);
        quoteCharacter = properties.get(DELIMITED_FORMATTER_QUOTE_CHARACTER);
        convertAttributeSettingsToAttributeOrdinal();
    }
    
    private void convertAttributeSettingsToAttributeOrdinal() {
        
        List<ComponentVersionAttributeSetting> attributeSettings = flowStep.getComponentVersion().getAttributeSettings();
        for (ComponentVersionAttributeSetting attributeSetting : attributeSettings) {
            if (attributeSetting.getName().equalsIgnoreCase(DELIMITED_FORMATTER_ATTRIBUTE_ORDINAL)) {
                attributes.add(new AttributeOrdinal(attributeSetting.getAttributeId(), Integer.parseInt(attributeSetting.getValue())));
            }
        }
        Collections.sort(attributes, new Comparator<AttributeOrdinal>() {
            @Override
            public int compare(AttributeOrdinal ordinal1, AttributeOrdinal ordinal2) {
                return ordinal1.getOrdinal() - ordinal2.getOrdinal();
            }
        });
    }
    
    private class AttributeOrdinal {
        
        public AttributeOrdinal(String attributeId, int ordinal) {
            this.attributeId = attributeId;
            this.ordinal = ordinal;
        }
        
        String attributeId;
        int ordinal;
        
        public String getAttributeId() {
            return attributeId;
        }

        public int getOrdinal() {
            return ordinal;
        }
    }
   
    //TODO: allow for groovy format functions
}