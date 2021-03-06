package org.sakaiproject.gradebookng;

import org.apache.wicket.core.request.handler.PageProvider;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.Application;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.session.ISessionStore;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.cycle.AbstractRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.settings.IExceptionSettings;
import org.apache.wicket.settings.IRequestCycleSettings.RenderStrategy;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.sakaiproject.gradebookng.framework.GradebookNgStringResourceLoader;
import org.sakaiproject.gradebookng.tool.pages.ErrorPage;
import org.sakaiproject.gradebookng.tool.pages.GradebookPage;
import org.sakaiproject.gradebookng.tool.pages.ImportExportPage;
import org.sakaiproject.gradebookng.tool.pages.PermissionsPage;
import org.sakaiproject.gradebookng.tool.pages.SettingsPage;

/**
 * Main application class
 *
 * @author Steve Swinsburg (steve.swinsburg@gmail.com)
 *
 */
public class GradebookNgApplication extends WebApplication {

	@Override
	public void init() {
		super.init();

		// page mounting for bookmarkable URLs
		mountPage("/grades", GradebookPage.class);
		mountPage("/settings", SettingsPage.class);
		mountPage("/importexport", ImportExportPage.class);
		mountPage("/permissions", PermissionsPage.class);

		// remove the version number from the URL so that browser refreshes re-render the page
		getRequestCycleSettings().setRenderStrategy(RenderStrategy.ONE_PASS_RENDER);

		// Configure for Spring injection
		getComponentInstantiationListeners().add(new SpringComponentInjector(this));

		// Add ResourceLoader that integrates with Sakai's Resource Loader
		getResourceSettings().getStringResourceLoaders().add(0, new GradebookNgStringResourceLoader());

		// Don't throw an exception if we are missing a property, just fallback
		getResourceSettings().setThrowExceptionOnMissingResource(false);

		// Remove the wicket specific tags from the generated markup
		getMarkupSettings().setStripWicketTags(true);

		// Don't add any extra tags around a disabled link (default is <em></em>)
		getMarkupSettings().setDefaultBeforeDisabledLink(null);
		getMarkupSettings().setDefaultAfterDisabledLink(null);

		// On Wicket session timeout, redirect to main page
		// getApplicationSettings().setPageExpiredErrorPage(getHomePage());

		// show internal error page rather than default developer page
		// for production, set to SHOW_NO_EXCEPTION_PAGE
		getExceptionSettings().setUnexpectedExceptionDisplay(IExceptionSettings.SHOW_EXCEPTION_PAGE);

		final ISessionStore sessionStore = getSessionStore();

		// Intercept any unexpected error stacktrace and take to our page
		getRequestCycleListeners().add(new AbstractRequestCycleListener() {
			@Override
			public IRequestHandler onException(final RequestCycle cycle, final Exception e) {
                            // FIXME: Disable this for production
				if (e instanceof ClassCastException) {
					System.err.println("INVALIDATING DUD SESSION");
					sessionStore.invalidate(cycle.getRequest());
				}

				return new RenderPageRequestHandler(new PageProvider(new ErrorPage(e)));
			}
		});

		// Disable Wicket's loading of jQuery - we load Sakai's preferred version in BasePage.java
		getJavaScriptLibrarySettings().setJQueryReference(new PackageResourceReference(GradebookNgApplication.class,"empty.js"));

		// cleanup the HTML
		getMarkupSettings().setStripWicketTags(true);
		getMarkupSettings().setStripComments(true);
		getMarkupSettings().setCompressWhitespace(true);

	}

	/**
	 * The main page for our app
	 *
	 * @see org.apache.wicket.Application#getHomePage()
	 */
	@Override
	public Class<GradebookPage> getHomePage() {
		return GradebookPage.class;
	}

	/**
	 * Constructor
	 */
	public GradebookNgApplication() {
	}

}
