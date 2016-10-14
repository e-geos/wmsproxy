package it.egeos.wmsproxy;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public class WmsProxy extends HttpServlet{
	private static final long serialVersionUID = 8990190272224562293L;
	private HttpClient client; 
	private MultiThreadedHttpConnectionManager connectionManager;
	private Logger log=Logger.getLogger(this.getClass().getSimpleName());
	
	@SuppressWarnings("serial")
	private ArrayList<String> to_drop=new ArrayList<String>(){{
		//TODO: put here any header (from client) you want to drop
	}};
	
	@SuppressWarnings("serial")
    @Override
	public void init(ServletConfig servletconfig) throws ServletException {
		super.init(servletconfig);
	
		connectionManager = new MultiThreadedHttpConnectionManager(){{
		    setParams(new HttpConnectionManagerParams(){{
		    	//TODO: set here connections config 
		    }});
		}};
		
		client = new HttpClient(connectionManager);
	}
			
    public void doGet(HttpServletRequest request,HttpServletResponse response) throws ServletException {
    	StringBuilder sb = logRequest(request);
    	try {
    		doAction(request, response,sb);
    		sb=logResponse(response,sb);			
    		log.log(Level.INFO,sb.toString());
    	} 
    	catch (Throwable throwable) {
    		log.log(Level.SEVERE,"Can't call doAction: "+throwable.getMessage(),throwable);
    		throw new ServletException(throwable);
    	}
    }
    
	private void doAction(final HttpServletRequest request, HttpServletResponse response,StringBuilder sb) throws Exception{		
		HttpMethodBase method =null;		  
	    try{		    	
			method = buildRequestMethod(request,client);		
			method.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
			logMethod(method,sb);
			client.executeMethod(method);			
			buildResponse(request, response, method);				
		}
		catch (Exception e) { 
			log.log(Level.SEVERE,"Errore comunicando con geoserver: "+e.getMessage(),e);
		}
	    finally{
	        if(method!=null)
	            method.releaseConnection();
	    }
	}
	
    private StringBuilder logRequest(HttpServletRequest request){
    	StringBuilder res=new StringBuilder();
    	res.append("Called proxy: "+this.getClass().getName()+"\n");
    	res.append(request.getMethod()+" "+request.getRequestURL());
    	String qs = request.getQueryString();
    	if (qs!=null && !qs.isEmpty())
    		res.append("?"+qs);
    	res.append("\n");
          
    	res.append("Headers: \n");
    	Enumeration<String> hnames = request.getHeaderNames();
    	String name;
        if(hnames!=null && hnames.hasMoreElements())
        	while(hnames.hasMoreElements()){
        		name=hnames.nextElement();
        		res.append("\t- "+name+": "+request.getHeader(name)+"\n");
        	}
        else
        	res.append("\t(No headers)\n");

        res.append("Cookies: \n");
        Cookie[] cookies = request.getCookies();
        if(cookies!=null && cookies.length>0)
        	for(Cookie c:request.getCookies())
        		res.append("\t- "+c.getName()+": "+c.getValue()+"\n");
        else
        	res.append("\t(No cookie)\n");

        res.append("Attributes: \n");
        Enumeration<String> anames = request.getAttributeNames();
        if(anames!=null && anames.hasMoreElements())
        	while(anames.hasMoreElements()){
        		name=anames.nextElement();
        		res.append("\t- "+name+": "+request.getAttribute(name)+"\n");
        	}
        else
        	res.append("\t(No attributes)\n");
        
        res.append("Parameters: \n");
        Enumeration<String> params = request.getParameterNames();
        if (params!=null && params.hasMoreElements())
        	while(params.hasMoreElements()){
        		String param = params.nextElement();
        		res.append("\t- "+param+": "+request.getParameter(param)+"\n");
        	}
        else
        	res.append("\t(No parameters)\n");
            
        return res;
    }
    
    private StringBuilder logMethod(HttpMethodBase method,StringBuilder res){
    	String url;
		try {
			url = method.getURI().toString();
		} 
		catch (URIException e) {
			url="Error: "+e.getMessage();
		}
		
    	res.append("\nBackend call: "+url);
    	res.append("\n");          
    	res.append("Headers: \n");
    	Header[] hnames = method.getRequestHeaders();
        if(hnames!=null && hnames.length>0)
        	for(Header h:hnames)
        		res.append("\t- "+h.getName()+": "+h.getValue()+"\n");
        else
        	res.append("\t(No headers)\n");
        return res;
    }

    private StringBuilder logResponse(HttpServletResponse response,StringBuilder res){
    	res.append("\nResponse from proxy:"+response.getStatus()+" "+response.getContentType()+"\n");
    	res.append("Headers: \n");
    	Collection<String> hnames = response.getHeaderNames();
        if(hnames!=null)
        	for(String name:hnames)
        		res.append("\t- "+name+": "+response.getHeader(name)+"\n");
        else
        	res.append("\t(No headers)\n");        
        return res;
    }

    private NameValuePair[] parseRequest(HttpServletRequest request,Map<String,String> overrides){    	
    	Map<String, String[]> parameters = request.getParameterMap();
    	ArrayList<NameValuePair> res=new ArrayList<NameValuePair>();
    	for(String p:parameters.keySet())
    		if(!to_drop.contains(p)) {
    			String o=overrides.get(p);    			
    			res.add(new NameValuePair(p,(o!=null)?o:StringUtils.join(parameters.get(p),',')));
    		}
    	return res.toArray(new NameValuePair[res.size()]);
    }
    
	private HttpMethodBase buildRequestMethod(HttpServletRequest request,HttpClient client) throws MalformedURLException{
		URL u=getBackendUrl(request);
		GetMethod method = new GetMethod(u.toString());
				
		//copy header
		Enumeration<String> headers = request.getHeaderNames();
		while(headers.hasMoreElements()){
			String h = headers.nextElement();
			method.setRequestHeader(h,request.getHeader(h));
		}
		
		//copy parameter
		method.setQueryString(parseRequest(request,getRequestOverrides(request)));
		
		//authentication        
		try {
			client.getState().setCredentials(new AuthScope(u.getHost(), u.getPort()), getCredentials());
			client.getParams().setAuthenticationPreemptive(true);
		} 
		catch (Exception e) {
			log.log(Level.SEVERE,"Impossibile generare le credenziali per geoserver, le richiestre verranno fatte in maniera anonima",e);			
		}		
		return method;
	}
	
	private void buildResponse(final HttpServletRequest request,HttpServletResponse response, HttpMethodBase method) throws IOException{
    	InputStream in=method.getResponseBodyAsStream();
    	for(Header hd:method.getResponseHeaders())
    		if (!hd.getName().equalsIgnoreCase("Transfer-Encoding"))
    			response.setHeader(hd.getName(), hd.getValue());    	

    	int st = method.getStatusCode();
    	response.setStatus(st==HttpServletResponse.SC_UNAUTHORIZED?HttpServletResponse.SC_FORBIDDEN:st);
    	setResponseHeaders(request,response);    	
    	    		
		int size=0;
		if (in!=null)
			size=IOUtils.copy(in, response.getOutputStream());
		response.setContentLength(size); 
		response.getOutputStream().flush();
		response.getOutputStream().close();
    }

	private URL getBackendUrl(HttpServletRequest request) {	
		try {
			//Write here your backend
			return new URL("http://localhost/geoserver/wms");
		} 
		catch (MalformedURLException e) {
			return null;
		}
	}
	
	private Credentials getCredentials(){		
		return new UsernamePasswordCredentials("admin", "geoserver");
	}
	
    @SuppressWarnings("serial")
	private Map<String, String> getRequestOverrides(HttpServletRequest request) {		
		return new HashMap<String, String>(){{
			//put here any request override
		}};
	}

	private void setResponseHeaders(final HttpServletRequest request,HttpServletResponse response){    	
    	//put here your custom headers for response
    }
}
