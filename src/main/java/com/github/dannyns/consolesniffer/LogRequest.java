package com.github.dannyns.consolesniffer;

public class LogRequest {

    private String  type;      // canonical event type
    private String  session;   // 8-char hex, unique per page load
    private Integer seq;       // 0-based monotonic sequence number within session
    private String  ts;        // ISO 8601 timestamp
    private String  url;       // window.location.href — SESSION_START only
    private String  ua;        // navigator.userAgent  — SESSION_START only
    private String  message;
    private String  source;    // script URL           — WINDOW_ERROR only
    private Integer line;      // error line number    — WINDOW_ERROR only
    private Integer col;       // error column number  — WINDOW_ERROR only
    private String  stack;     // stack trace          — WINDOW_ERROR, UNHANDLED_REJECTION
    private String  targetPath;

    public String  getType()              { return type; }
    public void    setType(String v)      { this.type = v; }

    public String  getSession()           { return session; }
    public void    setSession(String v)   { this.session = v; }

    public Integer getSeq()               { return seq; }
    public void    setSeq(Integer v)      { this.seq = v; }

    public String  getTs()                { return ts; }
    public void    setTs(String v)        { this.ts = v; }

    public String  getUrl()               { return url; }
    public void    setUrl(String v)       { this.url = v; }

    public String  getUa()                { return ua; }
    public void    setUa(String v)        { this.ua = v; }

    public String  getMessage()           { return message; }
    public void    setMessage(String v)   { this.message = v; }

    public String  getSource()            { return source; }
    public void    setSource(String v)    { this.source = v; }

    public Integer getLine()              { return line; }
    public void    setLine(Integer v)     { this.line = v; }

    public Integer getCol()               { return col; }
    public void    setCol(Integer v)      { this.col = v; }

    public String  getStack()             { return stack; }
    public void    setStack(String v)     { this.stack = v; }

    public String  getTargetPath()        { return targetPath; }
    public void    setTargetPath(String v){ this.targetPath = v; }
}
