package restserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import http.HTTPServer;
import http.HTTPServer.Operation;
import http.HTTPServer.Request;
import http.HTTPServer.Response;
import http.RESTProcessorUtil;
import tideengine.BackEndTideComputer;
import tideengine.TideStation;
import tideengine.TideUtilities;

import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class defines the REST operations supported by the HTTP Server.
 * <p>
 * This list is defined in the <code>List&lt;Operation&gt;</code> named <code>operations</code>.
 * <br>
 * Those operation mostly retrieve the state of the SunFlower class, and device.
 * <br>
 * The SunFlower will use the {@link #processRequest(Request)} method of this class to
 * have the required requests processed.
 * </p>
 */
public class RESTImplementation {

	private TideServer tideServer;

	private static SimpleDateFormat DURATION_FMT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	public RESTImplementation(TideServer ts) {

		this.tideServer = ts;
		// Check duplicates in operation list. Barfs if duplicate is found.
		RESTProcessorUtil.checkDuplicateOperations(operations);
	}

	/**
	 * Define all the REST operations to be managed
	 * by the HTTP server.
	 * <p>
	 * Frame path parameters with curly braces.
	 * <p>
	 * See {@link #processRequest(Request)}
	 * See {@link HTTPServer}
	 */
	private List<Operation> operations = Arrays.asList(
			new Operation(
					"GET",
					"/oplist",
					this::getOperationList,
					"List of all available operations."),
			new Operation(
					"GET",
					"/tide-stations",
					this::getStationsList,
					"Get Tide Stations list. Returns an array of Strings containing the Station full names. Paginable, supports 'limit' and 'offset' optional parameters. Default offset is 0, default limit is 500."),
			new Operation(
					"GET",
					"/tide-stations/{st-regex}",
					this::getStations,
					"Get Tide Stations matching the regex. Returns all data of the matching stations. Regex might need encoding/escaping."),
			new Operation(
					"POST",
					"/tide-stations/{station-name}/wh",
					this::getWaterHeight,
					"Creates a Water Height request for the {station}. Requires 2 query params: from, and to, in Duration format. Station Name might need encoding/escaping. Can also take a json body payload."));

	protected List<Operation> getOperations() {
		return this.operations;
	}

	/**
	 * This is the method to invoke to have a REST request processed as defined above.
	 *
	 * @param request as it comes from the client
	 * @return the actual result.
	 */
	public Response processRequest(Request request) throws UnsupportedOperationException {
		Optional<Operation> opOp = operations
				.stream()
				.filter(op -> op.getVerb().equals(request.getVerb()) && RESTProcessorUtil.pathMatches(op.getPath(), request.getPath()))
				.findFirst();
		if (opOp.isPresent()) {
			Operation op = opOp.get();
			request.setRequestPattern(op.getPath()); // To get the prms later on.
			Response processed = op.getFn().apply(request); // Execute here.
			return processed;
		} else {
			throw new UnsupportedOperationException(String.format("%s not managed", request.toString()));
		}
	}

	private Response getOperationList(Request request) {
		Response response = new Response(request.getProtocol(), Response.STATUS_OK);
		Operation[] channelArray = operations.stream()
				.collect(Collectors.toList())
				.toArray(new Operation[operations.size()]);
		String content = new Gson().toJson(channelArray);
		RESTProcessorUtil.generateResponseHeaders(response, content.length());
		response.setPayload(content.getBytes());
		return response;
	}

	/**
	 * Accepts limit and offset Query String parameters. Optional.
	 *
	 * @param request
	 * @return Encoded list (UTF-8)
	 */
	private Response getStationsList(Request request) {
		Response response = new Response(request.getProtocol(), Response.STATUS_OK);
		long offset = 0;
		long limit = 500;
		Map<String, String> qsPrms = request.getQueryStringParameters();
		if (qsPrms != null && qsPrms.containsKey("offset")) {
			try {
				offset = Long.parseLong(qsPrms.get("offset"));
			} catch (NumberFormatException nfe) {
				nfe.printStackTrace();
			}
		}
		if (qsPrms != null && qsPrms.containsKey("limit")) {
			try {
				limit = Long.parseLong(qsPrms.get("limit"));
			} catch (NumberFormatException nfe) {
				nfe.printStackTrace();
			}
		}
		try {
			List<String> stationNames = this.tideServer.getStationList().
					stream()
					.map(ts -> ts.getFullName())
					.skip(offset)
					.limit(limit)
					.collect(Collectors.toList());
			String content = new Gson().toJson(stationNames);
			RESTProcessorUtil.generateResponseHeaders(response, content.length());
			response.setPayload(content.getBytes());
			return response;
		} catch (Exception ex) {
			ex.printStackTrace();
			response.setStatus(Response.BAD_REQUEST);
			response.setPayload(ex.toString().getBytes());
			return response;
		}
	}

	/**
	 * Supports a payload in the body, in json format:
	 * <pre>
	 * {
	 *   "timezone": "Etc/UTC",
	 *   "step": 5,
	 *   "unit": "meters"|"feet"
	 * }
	 * </pre>
	 * <ul>
	 * <li>Default timezone is the timezone of the station</li>
	 * <li>Default step (in minutes) is 5</li>
	 * <li>Default unit is the unit of the station</li>
	 * </ul>
	 *
	 * @param request Requires two query string parameters <b>from</b> and <b>to</b>, in Duration format (yyyy-MM-ddThh:mm:ss)
	 * @return the expect response. Could contain an error, see the "TIDE-XXXX" messages.
	 */
	private Response getWaterHeight(Request request) {
		Response response = new Response(request.getProtocol(), Response.STATUS_OK); // Happy response
		List<String> prmValues = RESTProcessorUtil.getPrmValues(request.getRequestPattern(), request.getPath());
		String stationFullName = "";
		Calendar calFrom = null, calTo = null;
		boolean proceed = true;
		if (prmValues.size() == 1) {
			String param = prmValues.get(0);
			stationFullName = param;
		} else {
			response = HTTPServer.buildErrorResponse(response,
					Response.BAD_REQUEST,
					new HTTPServer.ErrorPayload()
							.errorCode("TIDE-0002")
							.errorMessage("Need tideServer path parameter {station-name}."));
			proceed = false;
		}
		if (proceed) {
			Map<String, String> prms = request.getQueryStringParameters();
			if (prms == null || prms.get("from") == null || prms.get("to") == null) {
				response = HTTPServer.buildErrorResponse(response,
						Response.BAD_REQUEST,
						new HTTPServer.ErrorPayload()
								.errorCode("TIDE-0003")
								.errorMessage("Query parameters 'from' and 'to' are required."));
				proceed = false;
			} else {
				String from = prms.get("from");
				String to = prms.get("to");
				try {
					Date fromDate = DURATION_FMT.parse(from);
					Date toDate = DURATION_FMT.parse(to);
					calFrom = Calendar.getInstance();
					calFrom.setTime(fromDate);
					calTo = Calendar.getInstance();
					calTo.setTime(toDate);
					if (calTo.before(calFrom)) {
						response = HTTPServer.buildErrorResponse(response,
								Response.BAD_REQUEST,
								new HTTPServer.ErrorPayload()
										.errorCode("TIDE-0014")
										.errorMessage(String.format("Bad date chronology. %s is after %s", from, to)));
						proceed = false;
					}
				} catch (ParseException pe) {
					response = HTTPServer.buildErrorResponse(response,
							Response.BAD_REQUEST,
							new HTTPServer.ErrorPayload()
									.errorCode("TIDE-0004")
									.errorMessage(pe.toString()));
					proceed = false;
				}
			}
			if (proceed) {
				final String stationName = stationFullName;
				int step = 5;
				String timeZoneToUse = null;
				unit unitToUse = null;
				// Payload in the body?
				if (request.getContent() != null && request.getContent().length > 0) {
					String payload = new String(request.getContent());
					if (!"null".equals(payload)) {
						Gson gson = new GsonBuilder().create();
						StringReader stringReader = new StringReader(payload);
						try {
							WaterHeightOptions options = gson.fromJson(stringReader, WaterHeightOptions.class);
							if (options.step == 0 &&
									options.timezone == null &&
									options.unit == null) {
								response = HTTPServer.buildErrorResponse(response,
										Response.BAD_REQUEST,
										new HTTPServer.ErrorPayload()
												.errorCode("TIDE-0011")
												.errorMessage(String.format("Invalid payload: %s", payload)));
								proceed = false;
							} else {
								if (options.step < 0) {
									response = HTTPServer.buildErrorResponse(response,
											Response.BAD_REQUEST,
											new HTTPServer.ErrorPayload()
													.errorCode("TIDE-0012")
													.errorMessage(String.format("Step MUST be positive: %d", options.step)));
									proceed = false;
								}
								if (proceed && options.timezone != null) {
									if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(options.timezone)) {
										response = HTTPServer.buildErrorResponse(response,
												Response.BAD_REQUEST,
												new HTTPServer.ErrorPayload()
														.errorCode("TIDE-0013")
														.errorMessage(String.format("Invalid TimeZone: %s", options.timezone)));
										proceed = false;
									}
								}
								if (proceed) {
									// Set overriden parameter values
									if (options.timezone != null) {
										timeZoneToUse = options.timezone;
									}
									if (options.unit != null) {
										unitToUse = options.unit;
									}
									if (options.step != 0) {
										step = options.step;
									}
								}
							}
						} catch (Exception ex) {
							response = HTTPServer.buildErrorResponse(response,
									Response.BAD_REQUEST,
									new HTTPServer.ErrorPayload()
											.errorCode("TIDE-0010")
											.errorMessage(ex.toString()));
							proceed = false;
						}
					}
				}
				if (proceed) {
					try {
						TideStation ts = null;
						Optional<TideStation> optTs = this.tideServer.getStationList().
								stream()
								.filter(station -> station.getFullName().equals(stationName))
								.findFirst();
						if (!optTs.isPresent()) {
							response = HTTPServer.buildErrorResponse(response,
									Response.NOT_FOUND,
									new HTTPServer.ErrorPayload()
											.errorCode("TIDE-0005")
											.errorMessage(String.format("Station [%s] not found", stationName)));
							proceed = false;
						} else {
							ts = optTs.get();
						}
						if (proceed) {
							// Calculate water height, from-to;
							TideTable tideTable = new TideTable();
							tideTable.stationName = stationName;
							tideTable.baseHeight = ts.getBaseHeight() * unitSwitcher(ts, unitToUse);
							tideTable.unit = (unitToUse != null ? unitToUse.toString() : ts.getDisplayUnit());
							Map<String, Double> map = new LinkedHashMap<>();

							Calendar now = calFrom;
							ts = BackEndTideComputer.findTideStation(stationFullName, now.get(Calendar.YEAR));
							if (ts != null) {
								now.setTimeZone(TimeZone.getTimeZone(timeZoneToUse != null ? timeZoneToUse : ts.getTimeZone()));
								while (now.before(calTo)) {
									double wh = TideUtilities.getWaterHeight(ts, this.tideServer.getConstSpeed(), now);
									TimeZone.setDefault(TimeZone.getTimeZone(timeZoneToUse != null ? timeZoneToUse : ts.getTimeZone())); // for TS Timezone display
//							  System.out.println((ts.isTideStation() ? "Water Height" : "Current Speed") + " in " + stationName + " at " + cal.getTime().toString() + " : " + TideUtilities.DF22PLUS.format(wh) + " " + ts.getDisplayUnit());
									map.put(now.getTime().toString(), wh * unitSwitcher(ts, unitToUse));
									now.add(Calendar.MINUTE, step);
								}
							} else {
								System.out.println("Wow!"); // I know...
							}
							tideTable.heights = map;
							/*
							 * Happy End
							 */
							String content = new Gson().toJson(tideTable);
							RESTProcessorUtil.generateResponseHeaders(response, content.length());
							response.setPayload(content.getBytes());
							return response;
						}
					} catch (Exception ex) {
						response = HTTPServer.buildErrorResponse(response,
								Response.BAD_REQUEST,
								new HTTPServer.ErrorPayload()
										.errorCode("TIDE-0006")
										.errorMessage(ex.toString()));
					}
				}
			}
		}
		return response; // If we reach here, something went wrong, it's a BAD_REQUEST or so.
	}

	private Response getStations(Request request) {
		Response response = new Response(request.getProtocol(), Response.STATUS_OK);
		List<String> prmValues = RESTProcessorUtil.getPrmValues(request.getRequestPattern(), request.getPath());
		final Pattern pattern;
		if (prmValues.size() == 1) {
			String nameRegex = prmValues.get(0);
			pattern = Pattern.compile(String.format(".*%s.*", nameRegex)); // decode/unescape
		} else {
			response = HTTPServer.buildErrorResponse(response,
					Response.BAD_REQUEST,
					new HTTPServer.ErrorPayload()
							.errorCode("TIDE-0008")
							.errorMessage("Need tideServer path parameter {regex}."));
			return response;
		}
		try {
			List<TideStation> ts = this.tideServer.getStationList().
					stream()
					.filter(station -> pattern.matcher(station.getFullName()).matches()) // TODO IgnoreCase?
					.collect(Collectors.toList());
			String content = new Gson().toJson(ts);
			RESTProcessorUtil.generateResponseHeaders(response, content.length());
			response.setPayload(content.getBytes());
			return response;
		} catch (Exception ex) {
			response = HTTPServer.buildErrorResponse(response,
					Response.BAD_REQUEST,
					new HTTPServer.ErrorPayload()
							.errorCode("TIDE-0009")
							.errorMessage(ex.toString()));
			return response;
		}
	}

	/**
	 * Can be used as a temporary placeholder when creating a new operation.
	 *
	 * @param request
	 * @return
	 */
	private Response emptyOperation(Request request) {
		Response response = new Response(request.getProtocol(), Response.STATUS_OK);
		return response;
	}

	private static class TideTable {
		String stationName;
		double baseHeight;
		String unit;
		Map<String, Double> heights;
	}

	private enum unit {
		meters, feet
	}

	private static double unitSwitcher(TideStation ts, unit overridden) {
		double factor = 1d;
		if (overridden != null) {
			if (!ts.getDisplayUnit().equals(overridden.toString())) {
				switch (ts.getDisplayUnit()) {
					case "feet": // feet to meters
						factor = 0.3048;
						break;
					case "meters": // meters to feet
						factor = 3.28084;
						break;
					default:
						break;
				}
			}
		}
		return factor;
	}

	private static class WaterHeightOptions {
		String timezone; // If not the Station timezone
		int step; // In minutes
		unit unit; // If not the station unit
	}
}
