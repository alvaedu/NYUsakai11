/******************************************************************************
**
** Advanced Distributed Learning Co-Laboratory (ADL Co-Lab) Hub grants you 
** ("Licensee") a non-exclusive, royalty free, license to use, modify and 
** redistribute this software in source and binary code form, provided that 
** i) this copyright notice and license appear on all copies of the software; 
** and ii) Licensee does not utilize the software in a manner which is 
** disparaging to ADL Co-Lab Hub.
**
** This software is provided "AS IS," without a warranty of any kind.  ALL 
** EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING 
** ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE 
** OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED.  ADL Co-Lab Hub AND ITS LICENSORS 
** SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF 
** USING, MODIFYING OR DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES.  IN NO 
** EVENT WILL ADL Co-Lab Hub OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, 
** PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, 
** INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE 
** THEORY OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE 
** SOFTWARE, EVEN IF ADL Co-Lab Hub HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH 
** DAMAGES.**
******************************************************************************/
package org.adl.util.debug;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <strong>Filename:</strong>  DebugIndicator.java<br><br>
 * <strong>Description:</strong>  This class is used to control debug 
 * statements to be sent to the Java Console.  The default is to turn off the 
 * ability to send these statements to the Java Console
 */
public class DebugIndicator {
	private static Log log = LogFactory.getLog(DebugIndicator.class);

	/**
	 * This controls display of log messages to the java console 
	 */
	/**
	 * This is copied from scorm/scorm-impl/adl/src/java/org/adl/util/debug/DebugIndicator.java
	 * to not use the static value of <em>false</em>.  Its comment was:
	 * "JLR 8/24/2007 -- wiring this up to log4j for consistency's sake."
	 * afs5@nyu.edu - 2015-11-17
	 */
	public static boolean ON = log.isDebugEnabled();
}
