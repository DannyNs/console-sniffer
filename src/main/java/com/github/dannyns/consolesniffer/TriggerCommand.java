package com.github.dannyns.consolesniffer;

public record TriggerCommand(
        String  command,   // click, dblclick, type, select, wait, waitFor, waitForHidden, find, assertExists, assertText, logPath, navigate
        String  selector,  // CSS selector for the target element
        String  text,      // text to type or assert
        String  value,     // value for select command
        Integer timeout,   // timeout in ms for waitFor, waitForHidden, find
        Integer ms,        // delay in ms for wait command
        Boolean clear,     // clear input before typing (default true)
        Boolean contains,  // substring match for assertText (default true)
        String  path       // URL path for navigate command
) {}
