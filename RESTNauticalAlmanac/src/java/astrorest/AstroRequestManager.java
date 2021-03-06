package astrorest;

import http.HTTPServer;
import http.RESTRequestManager;

import java.util.List;

public class AstroRequestManager implements RESTRequestManager {

	private boolean httpVerbose = "true".equals(System.getProperty("http.verbose", "false"));
	private RESTImplementation restImplementation;


	// See http://maia.usno.navy.mil/ser7/deltat.data
	private double deltaT = Double.parseDouble(System.getProperty("deltaT", Double.toString(68.8033))); // June 2017

	public AstroRequestManager() {
		System.out.println(String.format("Using Delta-T:%f", deltaT));
		restImplementation = new RESTImplementation(this);
	}

	/**
	 * Manage the REST requests.
	 *
	 * @param request incoming request
	 * @return as defined in the {@link RESTImplementation}
	 * @throws UnsupportedOperationException
	 */
	@Override
	public HTTPServer.Response onRequest(HTTPServer.Request request) throws UnsupportedOperationException {
		HTTPServer.Response response = restImplementation.processRequest(request); // All the skill is here.
		if (this.httpVerbose) {
			System.out.println("======================================");
			System.out.println("Request :\n" + request.toString());
			System.out.println("Response :\n" + response.toString());
			System.out.println("======================================");
		}
		return response;
	}

	@Override
	public List<HTTPServer.Operation> getRESTOperationList() {
		return restImplementation.getOperations();
	}
}
