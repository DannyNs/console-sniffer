package com.github.dannyns.consolesniffer;

import java.util.List;

public class TriggerScenario {

    private String  id;          // UUID, assigned by server
    private String  name;
    private String  description;
    private List<TriggerCommand> steps;
    private String  target;      // app identifier for multi-app routing
    private String  createdAt;   // ISO 8601, assigned by server

    public String  getTarget()                    { return target; }
    public void    setTarget(String v)            { this.target = v; }

    public String  getId()                        { return id; }
    public void    setId(String v)                { this.id = v; }

    public String  getName()                      { return name; }
    public void    setName(String v)              { this.name = v; }

    public String  getDescription()               { return description; }
    public void    setDescription(String v)       { this.description = v; }

    public List<TriggerCommand> getSteps()        { return steps; }
    public void    setSteps(List<TriggerCommand> v) { this.steps = v; }

    public String  getCreatedAt()                 { return createdAt; }
    public void    setCreatedAt(String v)         { this.createdAt = v; }
}
