package com.consolesniffer;

public class TriggerCommand {

    private String  command;   // click, dblclick, type, select, wait, waitFor, waitForHidden, find, assertExists, assertText
    private String  selector;  // CSS selector for the target element
    private String  text;      // text to type or assert
    private String  value;     // value for select command
    private Integer timeout;   // timeout in ms for waitFor, waitForHidden, find
    private Integer ms;        // delay in ms for wait command
    private Boolean clear;     // clear input before typing (default true)
    private Boolean contains;  // substring match for assertText (default true)

    public String  getCommand()              { return command; }
    public void    setCommand(String v)      { this.command = v; }

    public String  getSelector()             { return selector; }
    public void    setSelector(String v)     { this.selector = v; }

    public String  getText()                 { return text; }
    public void    setText(String v)         { this.text = v; }

    public String  getValue()                { return value; }
    public void    setValue(String v)         { this.value = v; }

    public Integer getTimeout()              { return timeout; }
    public void    setTimeout(Integer v)     { this.timeout = v; }

    public Integer getMs()                   { return ms; }
    public void    setMs(Integer v)          { this.ms = v; }

    public Boolean getClear()                { return clear; }
    public void    setClear(Boolean v)       { this.clear = v; }

    public Boolean getContains()             { return contains; }
    public void    setContains(Boolean v)    { this.contains = v; }
}
