/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */
package com.sun.tools.xjc.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import javax.servlet.ServletException;

import com.sun.xml.bind.webapp.LongProcessServlet;

/**
 * Generates javadoc from the generated code and serve the generated javadoc.
 * 
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class JavadocServlet extends LongProcessServlet  {

    protected Thread createTask() throws ServletException, IOException {
        System.out.println("launching javadoc");
        Compiler compiler = Compiler.get(request);
        if( compiler==null )   return null;
        else                   return new JavadocThread(compiler); 
    }

    protected void renderResult(Thread task) throws ServletException, IOException {
        JavadocThread javadoc = (JavadocThread)task;
        
        if( !javadoc.success ) {
            request.setAttribute("msg",new String(javadoc.statusMessage));
            forward( "/javadocError.jsp" );
        } else {
            forward( "/file/javadoc"+request.getPathInfo() );
        }
    }

    protected String getProgressTitle() {
        return "Generating Javadoc";
    }

    protected String getProgressMessage() {
        return "you'll be redirected to the javadoc shortly";
    }

    
    // the class should be static since it's pointless to access the instance
    // of Servlet given the thread model of it.
    private static class JavadocThread extends Thread {
    
        /** Used to limit the number of concurrent compilation to 1. */
        private static final Object lock = new Object();
        
        private final Compiler compiler;
        boolean success = false;
        String statusMessage = "";
        
        JavadocThread( Compiler compiler ) {
            this.compiler = compiler;
        
            // be cooperative
            setPriority(Thread.NORM_PRIORITY-1);
        }
        
        public void run() {
            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            
            synchronized(lock) {
                try {
                    
                    File outDir = compiler.getOutDir();
                    /*int r =*/ JavadocGenerator.process(
                        outDir,
                        new File(outDir,"javadoc"),
                        new ForkOutputStream(System.out,msg) );
                    
                    // javadoc doesn't seem to return the correct exit code.
                    // so always assume a success.
                    // success = (r==0);
                    success = true;
                } catch( Throwable e ) {
                    e.printStackTrace();
                    PrintStream ps = new PrintStream(msg);
                    e.printStackTrace(ps);
                    ps.flush();
                }
            }

            statusMessage = new String(msg.toByteArray());
        }
    }
}