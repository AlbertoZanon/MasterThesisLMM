/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 16.01.2015
 */
package com.albertozanon.TimeHomogeneousTest;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.junit.Assert;
import org.junit.Test;

import com.albertozanon.TimeHomogeneouLMM.LIBORMarketModelWithTenorRefinement;
import com.albertozanon.TimeHomogeneouLMM.CapletOnCompoundedBackward;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.marketdata.calibration.ParameterObject;
import net.finmath.marketdata.calibration.Solver;
import net.finmath.marketdata.model.AnalyticModel;
import net.finmath.marketdata.model.AnalyticModelFromCurvesAndVols;
import net.finmath.marketdata.model.curves.Curve;
import net.finmath.marketdata.model.curves.CurveInterpolation.ExtrapolationMethod;
import net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationEntity;
import net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationMethod;
import net.finmath.marketdata.model.volatilities.AbstractVolatilitySurface;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve;
import net.finmath.marketdata.model.curves.DiscountCurveInterpolation;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveFromDiscountCurve;
import net.finmath.marketdata.model.curves.ForwardCurveInterpolation;
import net.finmath.marketdata.products.AnalyticProduct;
import net.finmath.marketdata.products.Swap;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.interestrate.CalibrationProduct;
import net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.LIBORMonteCarloSimulationFromTermStructureModel;
import net.finmath.montecarlo.interestrate.TermStructureModel;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel;
import net.finmath.montecarlo.interestrate.models.covariance.AbstractLIBORCovarianceModelParametric;
import net.finmath.montecarlo.interestrate.models.covariance.BlendedLocalVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.DisplacedLocalVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModelExponentialDecay;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelExponentialForm5Param;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelFromVolatilityAndCorrelation;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelFourParameterExponentialForm;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelPiecewiseConstant;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelTimeHomogenousPiecewiseConstant;
import net.finmath.montecarlo.interestrate.models.covariance.TermStructCovarianceModelFromLIBORCovarianceModelParametric;
import net.finmath.montecarlo.interestrate.models.covariance.TermStructureCovarianceModelParametric;
import net.finmath.montecarlo.interestrate.models.covariance.TermStructureTenorTimeScalingInterface;
import net.finmath.montecarlo.interestrate.models.covariance.TermStructureTenorTimeScalingPicewiseConstant;
import net.finmath.montecarlo.interestrate.products.AbstractLIBORMonteCarloProduct;
import net.finmath.montecarlo.interestrate.products.Caplet;
import net.finmath.montecarlo.interestrate.products.SwaptionSimple;
import net.finmath.montecarlo.interestrate.products.Caplet.ValueUnit;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.optimizer.LevenbergMarquardt;
import net.finmath.optimizer.Optimizer;
import net.finmath.optimizer.OptimizerFactory;
import net.finmath.optimizer.OptimizerFactoryLevenbergMarquardt;
import net.finmath.optimizer.SolverException;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.Schedule;
import net.finmath.time.ScheduleGenerator;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;
import net.finmath.time.TimeDiscretizationFromArray.ShortPeriodLocation;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarExcludingTARGETHolidays;
import net.finmath.time.daycount.DayCountConvention_ACT_365;

/**
 * This class calibrate the time-homogeneous LMM on a swaption matrix.
 *
 */

public class LIBORMarketModelTimeHomogeneousCalibrationTest {


	private CalibrationProduct createCalibrationItem(final double weight, final double exerciseDate, final double swapPeriodLength, final int numberOfPeriods, final double moneyness, final double targetVolatility, final String targetVolatilityType, final ForwardCurve forwardCurve, final DiscountCurve discountCurve) throws CalculationException {

		final double[]	fixingDates			= new double[numberOfPeriods];
		final double[]	paymentDates		= new double[numberOfPeriods];
		final double[]	swapTenor			= new double[numberOfPeriods + 1];

		for (int periodStartIndex = 0; periodStartIndex < numberOfPeriods; periodStartIndex++) {
			fixingDates[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
			paymentDates[periodStartIndex] = exerciseDate + (periodStartIndex + 1) * swapPeriodLength;
			swapTenor[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
		}
		swapTenor[numberOfPeriods] = exerciseDate + numberOfPeriods * swapPeriodLength;

		// Swaptions swap rate
		final double swaprate = moneyness + getParSwaprate(forwardCurve, discountCurve, swapTenor);

		// Set swap rates for each period
		final double[] swaprates = new double[numberOfPeriods];
		Arrays.fill(swaprates, swaprate);

		/*
		 * We use Monte-Carlo calibration on implied volatility.
		 * Alternatively you may change here to Monte-Carlo valuation on price or
		 * use an analytic approximation formula, etc.
		 */
		
		final SwaptionSimple swaptionMonteCarlo = new SwaptionSimple(swaprate, swapTenor, SwaptionSimple.ValueUnit.valueOf(targetVolatilityType));
		//double targetValuePrice = AnalyticFormulas.blackModelSwaptionValue(swaprate, targetVolatility, fixingDates[0], swaprate, getSwapAnnuity(discountCurve, swapTenor));
		return new CalibrationProduct(swaptionMonteCarlo, targetVolatility, weight);
	}
	
	@Test
	public void testATMSwaptionCalibration() throws CalculationException, SolverException {
	
	    int numberOfPaths	= 2000;
		int numberOfFactors	= 1;

		 DecimalFormat formatterValue		= new DecimalFormat(" ##0.0000%;-##0.0000%", new DecimalFormatSymbols(Locale.ENGLISH));
		 DecimalFormat formatterParam		= new DecimalFormat(" #0.00000; -#0.00000", new DecimalFormatSymbols(Locale.ENGLISH));
		 DecimalFormat formatterDeviation	= new DecimalFormat(" 0.00000E00;-0.00000E00", new DecimalFormatSymbols(Locale.ENGLISH));

		System.out.println("Calibration to Swaptions:");

		final AnalyticModel curveModel = getCalibratedCurve();

		// Create the forward curve (initial value of the LIBOR market model)
		final ForwardCurve forwardCurve = curveModel.getForwardCurve("ForwardCurveFromDiscountCurve(discountCurve-EUR,1D)");

		final DiscountCurve discountCurve = curveModel.getDiscountCurve("discountCurve-EUR");

		/*
		 * Create a set of calibration products.
		 */
		final ArrayList<String>			calibrationItemNames	= new ArrayList<>();
		final ArrayList<CalibrationProduct>	calibrationProducts		= new ArrayList<>();

		final double	swapPeriodLength	= 0.5;

		final String[] atmExpiries = {
				"1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M",
				"2M", "2M", "2M", "2M", "2M", "2M", "2M", "2M", "2M", "2M", "2M", "2M", "2M", "2M", 
				"3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M",
				"6M", "6M", "6M", "6M", "6M", "6M", "6M", "6M", "6M", "6M", "6M", "6M", "6M", "6M",
				"9M", "9M", "9M", "9M", "9M", "9M", "9M", "9M", "9M", "9M", "9M", "9M", "9M", "9M",
				"1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", 
				"18M", "18M", "18M", "18M", "18M", "18M", "18M", "18M", "18M", "18M", "18M", "18M", "18M", "18M",
				"2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y",
				"3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "4Y",
				"4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "5Y", "5Y", "5Y", "5Y",
				"5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "7Y", "7Y", "7Y", "7Y", "7Y", "7Y", "7Y",
				"7Y", "7Y", "7Y", "7Y", "7Y", "7Y", "7Y", "10Y", "10Y", "10Y", "10Y", "10Y", "10Y", "10Y", "10Y", "10Y",
				"10Y", "10Y", "10Y", "10Y", "10Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y",
				"15Y", "15Y", "15Y", "15Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y",
				"20Y", "20Y", "20Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y",
				"25Y", "25Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y" };

		final String[] atmTenors = {
				"1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", 
				
				"1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y",
				"1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y",
				"1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y",

				"1Y", "2Y","3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y",
				"1Y", "2Y", "3Y", "4Y","5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y",
				"7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y",
				"9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y",
				"15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y",
				"25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y",
				"1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y",
				"3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y",
				"5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y",
				"7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y",
				"9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y",
				"15Y", "20Y", "25Y", "30Y" };

		final double[] atmNormalVolatilities = {
				0.0015335, 0.0015179, 0.0019499, 0.0024161, 0.0027817, 0.0031067, 0.0033722, 0.0035158, 0.0036656, 0.0037844, 0.00452, 0.0050913, 0.0054071, 0.0056496,
				//next is 2M
				0.0016709, 0.0016287, 0.0020182, 0.0024951, 0.002827, 0.0031023, 0.0034348, 0.0036183, 0.0038008, 0.0039155, 0.0046602, 0.0051981, 0.0055116, 0.0057249,
				
				0.0015543, 0.0016509, 0.0020863, 0.002587, 0.002949, 0.0032105, 0.0035338, 0.0037133, 0.0038475, 0.0040674, 0.0047458, 0.005276, 0.005476, 0.005793,
				0.0016777, 0.001937, 0.0023423, 0.0027823, 0.0031476, 0.0034569, 0.0037466, 0.0039852, 0.0041802, 0.0043221, 0.0049649, 0.0054206, 0.0057009, 0.0059071,
				//next is 9M
				0.0017809, 0.0020951, 0.0024978, 0.0029226, 0.0032379, 0.0035522, 0.0038397, 0.0040864, 0.0043122, 0.0044836, 0.0050939, 0.0054761, 0.0057374, 0.0059448,
				
				0.0020129, 0.0022865, 0.0027082, 0.0030921, 0.0033849, 0.0037107, 0.0039782, 0.0042058, 0.0044272, 0.0046082, 0.0051564, 0.0055307, 0.0057924, 0.0059811,
				//next is 18M
				0.0022824, 0.0025971, 0.0029895, 0.0033299, 0.0036346, 0.0039337, 0.0042153, 0.0044347, 0.0046686, 0.0048244, 0.0052739, 0.005604, 0.0058311, 0.0060011,
			
				0.0026477, 0.0029709, 0.0033639, 0.0036507, 0.0039096, 0.0041553, 0.0044241, 0.00462, 0.0048265, 0.004989, 0.005361, 0.0056565, 0.0058529, 0.0060102,
				0.003382, 0.0036593, 0.0039353, 0.0041484, 0.0043526, 0.0045677, 0.004775, 0.0049506, 0.0051159, 0.0052722, 0.0055185, 0.0057089, 0.0058555, 0.0059432,
				0.0040679, 0.0042363, 0.0044602, 0.0046206, 0.0047527, 0.0048998, 0.0050513, 0.0051928, 0.0053439, 0.0054657, 0.0056016, 0.0057244, 0.0058153, 0.0058793,
				0.0045508, 0.0046174, 0.0047712, 0.0048999, 0.0050364, 0.0051504, 0.0052623, 0.0053821, 0.0054941, 0.0055918, 0.0056569, 0.0057283, 0.0057752, 0.0058109,
				0.0051385, 0.0051373, 0.0052236, 0.005312, 0.0053793, 0.0054396, 0.0055037, 0.0055537, 0.0056213, 0.0056943, 0.005671, 0.0056707, 0.0056468, 0.0056423,
				0.0055069, 0.0054836, 0.0055329, 0.0055696, 0.005605, 0.0056229, 0.0056562, 0.005655, 0.0056679, 0.0057382, 0.0056494, 0.0055831, 0.0055096, 0.0054526,
				0.0054486, 0.0054057, 0.0054439, 0.005462, 0.0054915, 0.0054993, 0.0055134, 0.0054985, 0.0055318, 0.0055596, 0.005369, 0.0052513, 0.0051405, 0.0050416,
				0.005317, 0.005268, 0.005312, 0.0053112, 0.0053417, 0.0053556, 0.0053323, 0.0053251, 0.0053233, 0.0053126, 0.0050827, 0.004922, 0.0047924, 0.0046666,
				0.0051198, 0.0051013, 0.0051421, 0.0051418, 0.0051538, 0.005133, 0.0051081, 0.0050552, 0.005055, 0.0050473, 0.0048161, 0.0045965, 0.0044512, 0.0043099,
				0.0049482, 0.004947, 0.0049805, 0.0049951, 0.0050215, 0.0049849, 0.0049111, 0.0048498, 0.0047879, 0.0047688, 0.0044943, 0.0042786, 0.0041191, 0.0039756};


		final LocalDate referenceDate = LocalDate.of(2020, Month.JULY, 31);
		final BusinessdayCalendarExcludingTARGETHolidays cal = new BusinessdayCalendarExcludingTARGETHolidays();
		final DayCountConvention_ACT_365 modelDC = new DayCountConvention_ACT_365();
		for(int i=0; i<atmNormalVolatilities.length; i++ ) {

			final LocalDate exerciseDate = cal.getDateFromDateAndOffsetCode(referenceDate, atmExpiries[i]);
			final LocalDate tenorEndDate = cal.getDateFromDateAndOffsetCode(exerciseDate, atmTenors[i]);
			double	exercise		= modelDC.getDaycountFraction(referenceDate, exerciseDate); //periodo da oggi all'exercise date dello swaption
			double	tenor			= modelDC.getDaycountFraction(exerciseDate, tenorEndDate); //periodo dall'exercise date dello swaption alla fine dello swap product
			
			exercise = Math.round(exercise/0.25)*0.25;
			tenor = Math.round(tenor/0.25)*0.25;

			if(exercise < 0.25) {
				continue;
			}
//			if(exercise < 1.0) {
//				continue;
//			}

			final int		numberOfPeriods     = (int) Math.round(tenor / swapPeriodLength);
			final double	moneyness			= 0.0;
			final double	targetVolatility	= atmNormalVolatilities[i];

			final String	targetVolatilityType = "VOLATILITYNORMAL";

			final double	weight = 1.0;

			calibrationProducts.add(createCalibrationItem(weight, exercise, swapPeriodLength, numberOfPeriods, moneyness, targetVolatility, targetVolatilityType, forwardCurve, discountCurve));
			calibrationItemNames.add(atmExpiries[i]+"\t"+atmTenors[i]);
		}

			LIBORModelMonteCarloSimulationModel simulationCalibrated = null;
			double[] paramTimeScalingtTimeHomValues20seed33333 = {0.06432,-0.0057,-0.0056,-0.0021,0.0005,-0.0023,0.02879,-0.0078,0.00745,-0.0089,-0.0089,-0.0044,-0.0060,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089};
			double[] paramTimeScalingtTimeHomValues20seed12345 = {0.09,   0.01533, 0.01170,0.02 ,0.00987,0.00607,0.05088,-0.0051,0.00620,0.00752,-0.0089,-0.0036,-0.0043,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089};
			double[] paramTimeScalingtTimeHomValues40 = {-0.0090,-0.0090,-0.0090,0.00942,0.00826,-0.0090,-0.0090,0.09,0.01665,0.09,0.00758,0.07390,0.09,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089};
			
			double[] MIEI40paramTimeScalingVolParamUsatiperTest2Reb =  {0.09,0.09,-0.0089,-0.0089,-0.0089,-0.0089,-0.0009,0.09,-0.0089,0.09000,-0.0089,-0.0089,0.09,0.09,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0090,-0.0090,-0.0090,0.09};//LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelTimeHomogenousPiecewiseConstant(timeDiscretizationFromArray, liborPeriodDiscretization, new TimeDiscretizationFromArray(0.00, 0.50, 1.00, 2.00, 3.00, 4.00, 5.00, 7.00, 10.00, 15.00, 20.00, 25.00, 30.00 ), new double[] { 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0, 0.20/100.0 });
			double[] MIEI40paramTimeScalingVolParamTrovatiDaTest2Reb =  {0.09,0.09,0.09,0.09,0.09,-0.0089,0.09,0.08999,0.09,0.09,0.09,-0.0090,0.09,0.09,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,0.09,-0.0090,-0.0090,0.09,0.09,0.09,0.09,0.09,0.09,0.09,0.09,-0.0090,-0.0090,0.09,0.09,0.09,0.09,0.09,0.09,-0.0089,-0.0089,0.09};
			
			double[] MIEI80paramTimeScalingVolParamReb = {-0.0090,-0.0090,-0.0090,0.09,0.09,-0.0089,0.09,-0.0089,0.08999,-0.0089,-0.0089,-0.0089,0.09,-0.0089,-0.0089,-0.0089,0.09,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0089,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,0.09,0.09,0.09,0.09,0.09,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,0.09,0.09000,0.09,0.09,0.09,0.09,0.09,0.09,0.09,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090,-0.0090};
			//LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelPiecewiseConstant(timeDiscretizationFromArray, liborPeriodDiscretization, new TimeDiscretizationFromArray(0.00, 1.00, 2.00, 3.00, 4.00, 5.00, 6.00, 7.00, 8.00, 9.00, 10.00, 11.00, 12.00, 14.00, 15.00, 16.00, 17.00, 18.50, 20.00, 22.50, 25.00, 30.00 ), new TimeDiscretizationFromArray(0.00, 1.00, 2.00, 3.00, 4.00, 5.00, 6.00, 7.00, 8.00, 9.00, 10.00, 11.00, 12.00, 14.00, 15.00, 16.00, 17.00, 18.50, 20.00, 22.50, 25.00, 30.00 ), 0.40 / 100);	
//			final LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelPiecewiseConstant(timeDiscretizationFromArray, liborPeriodDiscretization, optionMaturityDiscretization, timeToMaturityDiscretization, 0.40 / 100, true);

//			If simulation time is below libor time, exceptions will be hard to track.
			final double lastTime	= 21.0;
			final double dt		= 0.005;
			final double dtSimulation	= 0.005;
			System.out.println("dtSimulation: " + dtSimulation);
			final TimeDiscretizationFromArray timeDiscretizationFromArray = new TimeDiscretizationFromArray(0.0, (int) (lastTime / dtSimulation), dtSimulation);
			final TimeDiscretization liborPeriodDiscretization =  new TimeDiscretizationFromArray(0.0, (int) (lastTime / dt), dt);
			int seed=3434;
			System.out.println("Seed: " + seed);
			final BrownianMotion brownianMotion = new net.finmath.montecarlo.BrownianMotionLazyInit(timeDiscretizationFromArray, numberOfFactors, numberOfPaths, seed /* seed */);
	
			//final TimeDiscretization timeToMaturityDiscretization = new TimeDiscretizationFromArray(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 7.0,  10.0, 15.0, 21.0);

			//TimeDiscretization timeToMaturityDiscretization = new TimeDiscretizationFromArray(0.00, 21, 1.0);
			TimeDiscretization timeToMaturityDiscretization = new TimeDiscretizationFromArray(0.00, 0.25, 0.5, 1.00, 2.00, 3.00, 5.00, 7.00, 10.0, 15.0, 21.0);		// needed if you use LIBORVolatilityModelPiecewiseConstantWithMercurioModification: TimeDiscretization  = new TimeDiscretizationFromArray(0.0, 0.25, 0.50, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 15.0, 20.0, 25.0, 30.0, 40.0);
			//TimeDiscretization timeToMaturityDiscretization = new TimeDiscretizationFromArray(0.00, 0.25,0.5,0.75, 1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0,11.0,12.0,13.0,14.0,15.0,16.0,17.0,18.0,19.0,20.0,21.0);
			double[] arrayValues = new double [timeToMaturityDiscretization.getNumberOfTimes()];
			for (int i=0; i<timeToMaturityDiscretization.getNumberOfTimes(); i++) {arrayValues[i]= 0.1/100;}
	
			LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelTimeHomogenousPiecewiseConstant(timeDiscretizationFromArray, liborPeriodDiscretization, timeToMaturityDiscretization, arrayValues);
		   //LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelFourParameterExponentialForm(timeDiscretizationFromArray, liborPeriodDiscretization, 0.2/100.0, 0.1/100.0, 0.15, 0.05/100.0, true); //0.20/100.0, 0.05/100.0, 0.10, 0.05/100.0, 

			final LIBORCorrelationModel correlationModel = new LIBORCorrelationModelExponentialDecay(timeDiscretizationFromArray, liborPeriodDiscretization, numberOfFactors, 0.05, false);

			//AbstractLIBORCovarianceModelParametric covarianceModelParametric = new LIBORCovarianceModelExponentialForm5Param(timeDiscretizationFromArray, liborPeriodDiscretization, numberOfFactors, new double[] { 0.20/100.0, 0.1/100.0, 0.10, 0.05/100.0, 0.10} );
			AbstractLIBORCovarianceModelParametric	covarianceModelParametric = new LIBORCovarianceModelFromVolatilityAndCorrelation(timeDiscretizationFromArray, liborPeriodDiscretization, volatilityModel, correlationModel);

			//final AbstractLIBORCovarianceModelParametric covarianceModelDisplaced = new DisplacedLocalVolatilityModel(covarianceModelParametric, 1.0/0.25, false /* isCalibrateable */);
//
//			final TimeDiscretization tenorTimeScalingDiscretization = new TimeDiscretizationFromArray(0.0, 21.0, 1.0, ShortPeriodLocation.SHORT_PERIOD_AT_START);
//			final double[] tenorTimeScalings = new double[tenorTimeScalingDiscretization.getNumberOfTimes()];
//
//			for (int j=0;j<tenorTimeScalingDiscretization.getNumberOfTimes()-1;j++)
//			{
//				tenorTimeScalings[j]=paramTimeScalingtTimeHomValues20seed33333[j];
//			}
//			
//			//Arrays.fill(tenorTimeScalings, 0.0);
//			final TermStructureTenorTimeScalingInterface tenorTimeScalingModel = new TermStructureTenorTimeScalingPicewiseConstant(tenorTimeScalingDiscretization, tenorTimeScalings);
//			
//			
			TermStructureCovarianceModelParametric termStructureCovarianceModel = new TermStructCovarianceModelFromLIBORCovarianceModelParametric(null, covarianceModelParametric );
//			termStructureCovarianceModel = termStructureCovarianceModel.getCloneWithModifiedParameters(MIEIparamTimeScalingVolParam);
			double[] bestParameters = null;
				int k=0;
				// Set model properties
				final Map<String, Object> properties = new HashMap<>();

				final Double accuracy = 1E-12;
				final int maxIterations = 400;
				final int numberOfThreads = 6;
				final OptimizerFactory optimizerFactory = new OptimizerFactoryLevenbergMarquardt(maxIterations, accuracy, numberOfThreads);

				final double[] parameterStandardDeviation = new double[termStructureCovarianceModel.getParameter().length];
				final double[] parameterLowerBound = new double[termStructureCovarianceModel.getParameter().length];
				final double[] parameterUpperBound = new double[termStructureCovarianceModel.getParameter().length];
				Arrays.fill(parameterStandardDeviation, k==0 ? 0.0020/100.0 : 0.2/100.0);
				Arrays.fill(parameterLowerBound, Double.NEGATIVE_INFINITY);
				Arrays.fill(parameterUpperBound, Double.POSITIVE_INFINITY);

				// Set calibration properties (should use our brownianMotion for calibration - needed to have to right correlation).
				final Map<String, Object> calibrationParameters = new HashMap<>();
				calibrationParameters.put("accuracy", accuracy);
				calibrationParameters.put("brownianMotion", brownianMotion);
				calibrationParameters.put("parameterStep", k == 0 ? new Double(1E-6) : new Double(5E-5) );
				calibrationParameters.put("optimizerFactory", optimizerFactory);
				properties.put("calibrationParameters", calibrationParameters);

//				System.out.println("Number of volatility parameters: " + volatilityModel.getParameter().length);
				//System.out.println("Number of correlation parameters: " + correlationModel.getParameter().length);
//				System.out.println("Number of scaling parameters: " + tenorTimeScalingModel.getParameter().length);

				System.out.println("Number of covariance parameters: " + termStructureCovarianceModel.getParameter().length);
				
//				System.out.println("Number of scalingAndVol parameters: " + scalingAndVol.length);

				/*
				 * Create corresponding LIBOR Market Model
				 * 7 days + 3 weeks + 2 month, 1 quarter + 39 semi-annuals
				 */
				final TimeDiscretization liborPeriodDiscretizationDaily = new TimeDiscretizationFromArray(0.0, 21.0, 0.005, ShortPeriodLocation.SHORT_PERIOD_AT_START);
				final TimeDiscretization liborPeriodDiscretizationWeekly = new TimeDiscretizationFromArray(0.0, 21.0, 0.025, ShortPeriodLocation.SHORT_PERIOD_AT_START);
				final TimeDiscretization liborPeriodDiscretizationMonthly = new TimeDiscretizationFromArray(0.0, 21.0, 0.125, ShortPeriodLocation.SHORT_PERIOD_AT_START);
				final TimeDiscretization liborPeriodDiscretizationQuarterly = new TimeDiscretizationFromArray(0.0, 21.0, 0.25, ShortPeriodLocation.SHORT_PERIOD_AT_START);
				final TimeDiscretization liborPeriodDiscretizationSemiannual = new TimeDiscretizationFromArray(0.0, 21.0, 0.5, ShortPeriodLocation.SHORT_PERIOD_AT_START);

				final TermStructureModel liborMarketModelCalibrated = new LIBORMarketModelWithTenorRefinement(
						new TimeDiscretization[] { liborPeriodDiscretizationDaily, liborPeriodDiscretizationWeekly, liborPeriodDiscretizationMonthly,liborPeriodDiscretizationQuarterly,liborPeriodDiscretizationSemiannual},
						new Integer[] { 5, 4, 3, 2, 200 },
						curveModel,
						forwardCurve,
						new DiscountCurveFromForwardCurve(forwardCurve),
						termStructureCovarianceModel,
						calibrationProducts.toArray(new CalibrationProduct[0]), 
						properties		
				);

				System.out.println("\nCalibrated parameters are:");
				final double[] param = ((LIBORMarketModelWithTenorRefinement) liborMarketModelCalibrated).getCovarianceModel().getParameter();
				//		((AbstractLIBORCovarianceModelParametric) liborMarketModelCalibrated.getCovarianceModel()).setParameter(param);
				for (final double p : param) {
					System.out.println(p);
				}
				bestParameters = param;

				final EulerSchemeFromProcessModel process = new EulerSchemeFromProcessModel(liborMarketModelCalibrated,brownianMotion);
				simulationCalibrated = new LIBORMonteCarloSimulationFromTermStructureModel( process);
			
				System.out.println("\nValuation on calibrated model:");
				double deviationSum			= 0.0;
				double deviationSquaredSum	= 0.0;
				
				for (int i = 0; i < calibrationProducts.size(); i++) {
					final AbstractLIBORMonteCarloProduct calibrationProduct = calibrationProducts.get(i).getProduct();
					try {
						final double valueModel = calibrationProduct.getValue(simulationCalibrated);
						final double valueTarget = calibrationProducts.get(i).getTargetValue().getAverage();
						final double error = valueModel-valueTarget;
						deviationSum += error;
						deviationSquaredSum += error*error;
						System.out.println(calibrationItemNames.get(i) + "\t" + "Model: " + formatterValue.format(valueModel) + "\t Target: " + formatterValue.format(valueTarget) + "\t Deviation: " + formatterDeviation.format(valueModel-valueTarget));// + "\t" + calibrationProduct.toString());
					}
					catch(final Exception e) {
					}
				}
		
				final double averageDeviation = deviationSum/calibrationProducts.size();
				System.out.println("Mean Deviation:" + formatterValue.format(averageDeviation));
				System.out.println("RMS Error.....:" + formatterValue.format(Math.sqrt(deviationSquaredSum/calibrationProducts.size())));
				System.out.println("__________________________________________________________________________________________\n");
		
				Assert.assertTrue(Math.abs(averageDeviation) < 1E-2);
				

//		// CAPLET ON BACKWARD LOOKING RATE SEMESTRALI
		DecimalFormat formatterTimeValue = new DecimalFormat("##0.00;");
		DecimalFormat formatterVolValue = new DecimalFormat("##0.00000;");
		DecimalFormat formatterAnalytic = new DecimalFormat("##0.000;");
		DecimalFormat formatterPercentage = new DecimalFormat(" ##0.000%;-##0.000%", new DecimalFormatSymbols(Locale.ENGLISH));
	
		double strike = 0.004783;
		double dtLibor = 0.5;
		
		double[] mktData = new double[] {/* 6M 0.00167, */ /* 12M*/ 0.00201, /* 18M*/ 0.00228, /* 2Y */ 0.00264, 0.0, /* 3Y */ 0.0033, /* 4Y */0.00406, /* 5Y */ 0.00455, /* 6Y - NA */ 0.0, /* 7Y */0.00513, /* 8Y- NA */0.0, /* 9Y */0.0, /* 10Y */0.00550,0.0,0.0,0.0,0.0, /* 15Y */0.00544,0.0,0.0,0.0,0.0, /* 20Y */0.0053,0.0,0.0,0.0};
	
		int mktDataIndex = 0;

		
		//Results with CALIBRATED model
		System.out.println("\n results on CALIBRATED model \n");
		double beginLiborTime = 0.5;
		while(beginLiborTime < liborPeriodDiscretization.getTime(liborPeriodDiscretization.getNumberOfTimes()-1)) {
			Caplet capletCassical = new Caplet(beginLiborTime, dtLibor, strike, dtLibor, false, ValueUnit.NORMALVOLATILITY);
			//set to default ValueUnit.NORMALVOLATILITY for CapletOnCompoundedBackward
			CapletOnCompoundedBackward backwardCaplet = new CapletOnCompoundedBackward(beginLiborTime, dtLibor, strike, dtLibor,false);
			double capletMaturity = beginLiborTime+dtLibor;
			double impliedVolClassical = capletCassical.getValue(simulationCalibrated);
			double impliedVolBackward = backwardCaplet.getValue(simulationCalibrated);
			double analyticFormulaPaper = Math.sqrt(1+0.5/(beginLiborTime*3));
			double ratioImpliedVol = impliedVolBackward/impliedVolClassical;
			double error = (analyticFormulaPaper-ratioImpliedVol)/analyticFormulaPaper;
			
			if (beginLiborTime<2.5) {		//da i valori del caplet per maturity 1.0Y, 1.5Y, 2Y, 2.5Y, 3.0Y.	
				if (mktData[mktDataIndex] == 0.0) {
				System.out.println("Caplet on B(" + formatterTimeValue.format(beginLiborTime) + ", "
						+ formatterTimeValue.format(capletMaturity) + "; "
						+ formatterTimeValue.format(capletMaturity) + ")." + "\t" + "Backward caplet: "
						+ formatterPercentage.format(impliedVolBackward)+ "\t" + "Classical Caplet: " 
						+ formatterPercentage.format(impliedVolClassical)+ "\t" + "Analytic ratio: " 
						+ formatterAnalytic.format(analyticFormulaPaper) +  "\t" + "MC ratio: "
						+ formatterAnalytic.format(ratioImpliedVol) +  "\t" +"Error: "
					+ formatterPercentage.format(error) );
				beginLiborTime+=0.5;
				mktDataIndex+=1;
				}
				else {

					double ratioMktVol =impliedVolBackward/mktData[mktDataIndex];
					double errorMkt = (analyticFormulaPaper-ratioMktVol)/analyticFormulaPaper;

					System.out.println("Caplet on B(" + formatterTimeValue.format(beginLiborTime) + ", "
							+ formatterTimeValue.format(capletMaturity) + "; "
							+ formatterTimeValue.format(capletMaturity) + ")." + "\t" + "Backward caplet: "
							+ formatterPercentage.format(impliedVolBackward)+ "\t" + "Classical Caplet: " 
							+ formatterPercentage.format(impliedVolClassical)+ "\t" + "Analytic ratio: " 
							+ formatterAnalytic.format(analyticFormulaPaper) +  "\t" + "MC ratio: "
							+ formatterAnalytic.format(ratioImpliedVol) +  "\t" +"Error: "
							+ formatterPercentage.format(error) 
							+  "\t" +"Market Caplet Vol. " + formatterPercentage.format(mktData[mktDataIndex])
							+  "\t" +"Market Ratio: "	+ formatterAnalytic.format(ratioMktVol)	
							+  "\t" +"Market Error: "	+ formatterPercentage.format(errorMkt)
							);
					beginLiborTime+=0.5;
					mktDataIndex+=1;
				}
				
			}
			else {	//secondo loop da i valori del caplet per maturity 4Y,5Y,...,21
	
				if (mktData[mktDataIndex] == 0.0) {
					System.out.println("Caplet on B(" + formatterTimeValue.format(beginLiborTime) + ", "
							+ formatterTimeValue.format(capletMaturity) + "; "
							+ formatterTimeValue.format(capletMaturity) + ")." + "\t" + "Backward caplet: "
							+ formatterPercentage.format(impliedVolBackward)+ "\t" + "Classical Caplet: " 
							+ formatterPercentage.format(impliedVolClassical)+ "\t" + "Analytic ratio: " 
							+ formatterAnalytic.format(analyticFormulaPaper) +  "\t" + "MC ratio: "
							+ formatterAnalytic.format(ratioImpliedVol) +  "\t" +"Error: "
						+ formatterPercentage.format(error) );
					beginLiborTime+=1;
					mktDataIndex+=1;
					}
					else {
						double ratioMktVol =impliedVolBackward/mktData[mktDataIndex];
						double errorMkt = (analyticFormulaPaper-ratioMktVol)/analyticFormulaPaper;

						System.out.println("Caplet on B(" + formatterTimeValue.format(beginLiborTime) + ", "
								+ formatterTimeValue.format(capletMaturity) + "; "
								+ formatterTimeValue.format(capletMaturity) + ")." + "\t" + "Backward caplet: "
								+ formatterPercentage.format(impliedVolBackward)+ "\t" + "Classical Caplet: " 
								+ formatterPercentage.format(impliedVolClassical)+ "\t" + "Analytic ratio: " 
								+ formatterAnalytic.format(analyticFormulaPaper) +  "\t" + "MC ratio: "
								+ formatterAnalytic.format(ratioImpliedVol) +  "\t" +"Error: "
								+ formatterPercentage.format(error) 
								+  "\t" +"Market Caplet Vol. " + formatterPercentage.format(mktData[mktDataIndex])
								+  "\t" +"Market Ratio: "	+ formatterAnalytic.format(ratioMktVol)	
								+  "\t" +"Market Error: "	+ formatterPercentage.format(errorMkt)
								);
						beginLiborTime+=1;
						mktDataIndex+=1;
					}
			}
		}
		
		System.out.println("\n Backward looking rate:");
		for(beginLiborTime=0.0; beginLiborTime<liborPeriodDiscretization.getTime(liborPeriodDiscretization.getNumberOfTimes()-1); beginLiborTime+=1) {
			double endLiborTime=beginLiborTime+dtLibor;
			RandomVariable backwardLookingRate =  simulationCalibrated.getBackward(endLiborTime, beginLiborTime,endLiborTime);
			double avgBackwardLookingRate =backwardLookingRate.getAverage();
			System.out.println("Backward B(" + formatterTimeValue.format(beginLiborTime) +  ", " + formatterTimeValue.format(endLiborTime) + "; "  + formatterTimeValue.format(endLiborTime)+ ")."+ "\t" + "Average: " + avgBackwardLookingRate);
		}
		
		System.out.println("\n LIBOR rate:");
		for(beginLiborTime=0.0; beginLiborTime<liborPeriodDiscretization.getTime(liborPeriodDiscretization.getNumberOfTimes()-1); beginLiborTime+=1) {
			double endLiborTime=beginLiborTime+dtLibor;
			RandomVariable libor =  simulationCalibrated.getLIBOR(beginLiborTime, beginLiborTime,endLiborTime);
			double avgBackwardLookingRate = libor.getAverage();
			System.out.println("Backward B(" + formatterTimeValue.format(beginLiborTime) +  ", " + formatterTimeValue.format(endLiborTime) + "; "  + formatterTimeValue.format(beginLiborTime)+ ")."+ "\t" + "Average: " + avgBackwardLookingRate);
		}
		
		
		
		final CalibrationProduct[] calibrationItems = new CalibrationProduct[0];

		final TermStructureModel  liborMarketModelNOTCalibrated = new LIBORMarketModelWithTenorRefinement (
				new TimeDiscretization[] { liborPeriodDiscretizationDaily, liborPeriodDiscretizationWeekly, liborPeriodDiscretizationMonthly,liborPeriodDiscretizationQuarterly,liborPeriodDiscretizationSemiannual},
				new Integer[] { 5, 4, 3, 2, 200 },
				curveModel,
				forwardCurve,
				new DiscountCurveFromForwardCurve(forwardCurve),
				termStructureCovarianceModel,
				calibrationItems,
				null		
		);

		final EulerSchemeFromProcessModel process2 = new EulerSchemeFromProcessModel(liborMarketModelNOTCalibrated,brownianMotion);
		LIBORModelMonteCarloSimulationModel  simulationNONCalibrated = new LIBORMonteCarloSimulationFromTermStructureModel ( process2);

		
		System.out.println("\n results on NON CALIBRATED model \n");
		
		 beginLiborTime = 0.5;
		mktDataIndex = 0;

		while(beginLiborTime < liborPeriodDiscretization.getTime(liborPeriodDiscretization.getNumberOfTimes()-1)) {
			Caplet capletCassical = new Caplet(beginLiborTime, dtLibor, strike, dtLibor, false, ValueUnit.NORMALVOLATILITY);
			CapletOnCompoundedBackward backwardCaplet = new CapletOnCompoundedBackward(beginLiborTime, dtLibor, strike, dtLibor,false);
			double capletMaturity = beginLiborTime+dtLibor;
			double impliedVolClassical = capletCassical.getValue(simulationNONCalibrated);
			double impliedVolBackward = backwardCaplet.getValue(simulationNONCalibrated);
			double analyticFormulaPaper = Math.sqrt(1+0.5/(beginLiborTime*3));
			double ratioImpliedVol = impliedVolBackward/impliedVolClassical;
			double error = (analyticFormulaPaper-ratioImpliedVol)/analyticFormulaPaper;

			
			if (beginLiborTime<2.5) {		//da i valori del caplet per maturity 1.5Y, 2Y, 2.5Y,.	
				if (mktData[mktDataIndex] == 0.0) {
				System.out.println("Caplet on B(" + formatterTimeValue.format(beginLiborTime) + ", "
						+ formatterTimeValue.format(capletMaturity) + "; "
						+ formatterTimeValue.format(capletMaturity) + ")." + "\t" + "Backward caplet: "
						+ formatterPercentage.format(impliedVolBackward)+ "\t" + "Classical Caplet: " 
						+ formatterPercentage.format(impliedVolClassical)+ "\t" + "Analytic ratio: " 
						+ formatterAnalytic.format(analyticFormulaPaper) +  "\t" + "MC ratio: "
						+ formatterAnalytic.format(ratioImpliedVol) +  "\t" +"Error: "
					+ formatterPercentage.format(error) );
				beginLiborTime+=0.5;
				mktDataIndex+=1;
				}
				else {

					double ratioMktVol =impliedVolBackward/mktData[mktDataIndex];
					double errorMkt = (analyticFormulaPaper-ratioMktVol)/analyticFormulaPaper;

					System.out.println("Caplet on B(" + formatterTimeValue.format(beginLiborTime) + ", "
							+ formatterTimeValue.format(capletMaturity) + "; "
							+ formatterTimeValue.format(capletMaturity) + ")." + "\t" + "Backward caplet: "
							+ formatterPercentage.format(impliedVolBackward)+ "\t" + "Classical Caplet: " 
							+ formatterPercentage.format(impliedVolClassical)+ "\t" + "Analytic ratio: " 
							+ formatterAnalytic.format(analyticFormulaPaper) +  "\t" + "MC ratio: "
							+ formatterAnalytic.format(ratioImpliedVol) +  "\t" +"Error: "
							+ formatterPercentage.format(error) 
							+  "\t" +"Market Caplet Vol. " + formatterPercentage.format(mktData[mktDataIndex])
							+  "\t" +"Market Ratio: "	+ formatterAnalytic.format(ratioMktVol)	
							+  "\t" +"Market Error: "	+ formatterPercentage.format(errorMkt)
							);
					beginLiborTime+=0.5;
					mktDataIndex+=1;
				}
				
			}
			else {	//secondo loop da i valori del caplet per maturity 4Y,5Y,...,21
	
				if (mktData[mktDataIndex] == 0.0) {
					System.out.println("Caplet on B(" + formatterTimeValue.format(beginLiborTime) + ", "
							+ formatterTimeValue.format(capletMaturity) + "; "
							+ formatterTimeValue.format(capletMaturity) + ")." + "\t" + "Backward caplet: "
							+ formatterPercentage.format(impliedVolBackward)+ "\t" + "Classical Caplet: " 
							+ formatterPercentage.format(impliedVolClassical)+ "\t" + "Analytic ratio: " 
							+ formatterAnalytic.format(analyticFormulaPaper) +  "\t" + "MC ratio: "
							+ formatterAnalytic.format(ratioImpliedVol) +  "\t" +"Error: "
						+ formatterPercentage.format(error) );
					beginLiborTime+=1;
					mktDataIndex+=1;
					}
					else {
						double ratioMktVol =impliedVolBackward/mktData[mktDataIndex];
						double errorMkt = (analyticFormulaPaper-ratioMktVol)/analyticFormulaPaper;

						System.out.println("Caplet on B(" + formatterTimeValue.format(beginLiborTime) + ", "
								+ formatterTimeValue.format(capletMaturity) + "; "
								+ formatterTimeValue.format(capletMaturity) + ")." + "\t" + "Backward caplet: "
								+ formatterPercentage.format(impliedVolBackward)+ "\t" + "Classical Caplet: " 
								+ formatterPercentage.format(impliedVolClassical)+ "\t" + "Analytic ratio: " 
								+ formatterAnalytic.format(analyticFormulaPaper) +  "\t" + "MC ratio: "
								+ formatterAnalytic.format(ratioImpliedVol) +  "\t" +"Error: "
								+ formatterPercentage.format(error) 
								+  "\t" +"Market Caplet Vol. " + formatterPercentage.format(mktData[mktDataIndex])
								+  "\t" +"Market Ratio: "	+ formatterAnalytic.format(ratioMktVol)	
								+  "\t" +"Market Error: "	+ formatterPercentage.format(errorMkt)
								);
						beginLiborTime+=1;
						mktDataIndex+=1;
					}
			}
		}
		
		System.out.println("\n Backward looking rate:");
		for(beginLiborTime=0.0; beginLiborTime<liborPeriodDiscretization.getTime(liborPeriodDiscretization.getNumberOfTimes()-1); beginLiborTime+=1) {
			double endLiborTime=beginLiborTime+dtLibor;
			RandomVariable backwardLookingRate =  simulationNONCalibrated.getBackward(endLiborTime, beginLiborTime,endLiborTime);
			double avgBackwardLookingRate =backwardLookingRate.getAverage();
			System.out.println("Backward B(" + formatterTimeValue.format(beginLiborTime) +  ", " + formatterTimeValue.format(endLiborTime) + "; "  + formatterTimeValue.format(endLiborTime)+ ")."+ "\t" + "Average: " + avgBackwardLookingRate);
		}
		
		System.out.println("\n LIBOR rate:");
		for(beginLiborTime=0.0; beginLiborTime<liborPeriodDiscretization.getTime(liborPeriodDiscretization.getNumberOfTimes()-1); beginLiborTime+=1) {
			double endLiborTime=beginLiborTime+dtLibor;
			RandomVariable libor =  simulationNONCalibrated.getLIBOR(beginLiborTime, beginLiborTime,endLiborTime);
			double avgBackwardLookingRate = libor.getAverage();
			System.out.println("Backward B(" + formatterTimeValue.format(beginLiborTime) +  ", " + formatterTimeValue.format(endLiborTime) + "; "  + formatterTimeValue.format(beginLiborTime)+ ")."+ "\t" + "Average: " + avgBackwardLookingRate);
		}
		
	}

		
		
		public AnalyticModel getCalibratedCurve() throws SolverException {
			final String[] maturity					= { "1D", "7D", "14D", "21D", "1M", "2M", "3M", "4M", "5M", "6M", "7M", "8M", "9M", "12M", "15M", "18M", "21M", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "11Y", "12Y", "15Y", "20Y", "25Y", "30Y", "40Y", "50Y" };
			final String[] frequency				= { "tenor", "tenor", "tenor",  "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual"};
			final String[] frequencyFloat			= { "tenor", "tenor", "tenor",  "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "tenor", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual"};
			final String[] daycountConventions	    = { "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360"};
			final String[] daycountConventionsFloat	= { "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360"};
			final double[] rates					= { -0.0055, -0.00553, -0.00553, -0.00553, -0.00553, -0.00555, -0.00556, -0.00559, -0.00564, -0.00568, -0.00572, -0.00577, -0.00581, -0.00592, -0.00601, -0.00608, -0.00613, -0.00619, -0.00627, -0.00622, -0.00606, -0.00582, -0.00553, -0.00519, -0.00482, -0.00442, -0.00402, -0.00362, -0.00261, -0.00189, -0.00197, -0.0023, -0.00286, -0.00333};
			final HashMap<String, Object> parameters = new HashMap<>();

			parameters.put("referenceDate", LocalDate.of(2020, Month.JULY, 31));
			parameters.put("currency", "EUR");
			parameters.put("forwardCurveTenor", "1D");
			parameters.put("maturities", maturity);
			parameters.put("fixLegFrequencies", frequency);
			parameters.put("floatLegFrequencies", frequencyFloat);
			parameters.put("fixLegDaycountConventions", daycountConventions);
			parameters.put("floatLegDaycountConventions", daycountConventionsFloat);
			parameters.put("rates", rates);

		return getCalibratedCurve(null, parameters);
	}

	private static AnalyticModel getCalibratedCurve(final AnalyticModel model2, final Map<String, Object> parameters) throws SolverException {

		final LocalDate	referenceDate		= (LocalDate) parameters.get("referenceDate");
		final String	currency			= (String) parameters.get("currency");
		final String	forwardCurveTenor	= (String) parameters.get("forwardCurveTenor");
		final String[]	maturities			= (String[]) parameters.get("maturities");
		final String[]	frequency			= (String[]) parameters.get("fixLegFrequencies");
		final String[]	frequencyFloat		= (String[]) parameters.get("floatLegFrequencies");
		final String[]	daycountConventions	= (String[]) parameters.get("fixLegDaycountConventions");
		final String[]	daycountConventionsFloat	= (String[]) parameters.get("floatLegDaycountConventions");
		final double[]	rates						= (double[]) parameters.get("rates");

		Assert.assertEquals(maturities.length, frequency.length);
		Assert.assertEquals(maturities.length, daycountConventions.length);
		Assert.assertEquals(maturities.length, rates.length);

		Assert.assertEquals(frequency.length, frequencyFloat.length);
		Assert.assertEquals(daycountConventions.length, daycountConventionsFloat.length);

		final int		spotOffsetDays = 2;
		final String	forwardStartPeriod = "0D";

		final String curveNameDiscount = "discountCurve-" + currency;

		/*
		 * We create a forward curve by referencing the same discount curve, since
		 * this is a single curve setup.
		 *
		 * Note that using an independent NSS forward curve with its own NSS parameters
		 * would result in a problem where both, the forward curve and the discount curve
		 * have free parameters.
		 */
		final ForwardCurve forwardCurve		= new ForwardCurveFromDiscountCurve(curveNameDiscount, referenceDate, forwardCurveTenor);

		// Create a collection of objective functions (calibration products)
		final Vector<AnalyticProduct> calibrationProducts = new Vector<>();
		final double[] curveMaturities	= new double[rates.length+1];
		final double[] curveValue			= new double[rates.length+1];
		final boolean[] curveIsParameter	= new boolean[rates.length+1];
		curveMaturities[0] = 0.0;
		curveValue[0] = 1.0;
		curveIsParameter[0] = false;
		for(int i=0; i<rates.length; i++) {
//			scheduleRec=scheduleReceiveLeg; schedulePay=schedulePayLeg
			final Schedule schedulePay = ScheduleGenerator.createScheduleFromConventions(referenceDate, spotOffsetDays, forwardStartPeriod, maturities[i], frequency[i], daycountConventions[i], "first", "following", new BusinessdayCalendarExcludingTARGETHolidays(), -2, 0);
			final Schedule scheduleRec = ScheduleGenerator.createScheduleFromConventions(referenceDate, spotOffsetDays, forwardStartPeriod, maturities[i], frequencyFloat[i], daycountConventionsFloat[i], "first", "following", new BusinessdayCalendarExcludingTARGETHolidays(), -2, 0);

			curveMaturities[i+1] = Math.max(schedulePay.getPayment(schedulePay.getNumberOfPeriods()-1),scheduleRec.getPayment(scheduleRec.getNumberOfPeriods()-1));
			curveValue[i+1] = 1.0;
			curveIsParameter[i+1] = true;
			calibrationProducts.add(new Swap(schedulePay, null, rates[i], curveNameDiscount, scheduleRec, forwardCurve.getName(), 0.0, curveNameDiscount));
		} 

		final InterpolationMethod interpolationMethod = InterpolationMethod.LINEAR;

		// Create a discount curve
		final DiscountCurveInterpolation discountCurveInterpolation = DiscountCurveInterpolation.createDiscountCurveFromDiscountFactors(
				curveNameDiscount								/* name */,
				referenceDate	/* referenceDate */,
				curveMaturities	/* maturities */,
				curveValue		/* discount factors */,
				curveIsParameter,
				interpolationMethod ,
				ExtrapolationMethod.CONSTANT,
				InterpolationEntity.LOG_OF_VALUE
				);

		/*
		 * Model consists of the two curves, but only one of them provides free parameters.
		 */
		AnalyticModel model = new AnalyticModelFromCurvesAndVols(new Curve[] { discountCurveInterpolation, forwardCurve });

		/*
		 * Create a collection of curves to calibrate
		 */
		final Set<ParameterObject> curvesToCalibrate = new HashSet<>();
		curvesToCalibrate.add(discountCurveInterpolation);

		/*
		 * Calibrate the curve
		 */
		final Solver solver = new Solver(model, calibrationProducts);
		final AnalyticModel calibratedModel = solver.getCalibratedModel(curvesToCalibrate);
		System.out.println("Solver reported acccurary....: " + solver.getAccuracy());

		Assert.assertEquals("Calibration accurarcy", 0.0, solver.getAccuracy(), 1E-3);

		// Get best parameters
		final double[] parametersBest = calibratedModel.getDiscountCurve(discountCurveInterpolation.getName()).getParameter();

		// Test calibration
		model			= calibratedModel;

//--> praticamente il calibrato valore di calibrationProducts (che sono swap) deve essere = 0 --> penso perchè il prezzo dello swap deve essere = 0 per definizione, quindi in teoria gli interest definiti sono L_i - S e quando calibri questa differenza deve essere =0 (e se così è allora il prezzo dello swap è 0) --> avrebbe anche senso perchè se noti il payer ha tassi prima negativi e poi positivi, mentre quell'altro ha tassi sempre = 0 (cioè L=S, cioè è quello che paga il fixed swap rate)
		double squaredErrorSum = 0.0;
		for(final AnalyticProduct c : calibrationProducts) {
			final double value = c.getValue(0.0, model);
			final double valueTaget = 0.0;
			final double error = value - valueTaget;
			squaredErrorSum += error*error;
		}
		final double rms = Math.sqrt(squaredErrorSum/calibrationProducts.size());

		System.out.println("Independent checked acccurary: " + rms);

		System.out.println("Calibrated discount curve: ");
		for(int i=0; i<curveMaturities.length; i++) {
			final double maturity = curveMaturities[i];
			System.out.println(maturity + "\t" + calibratedModel.getDiscountCurve(discountCurveInterpolation.getName()).getDiscountFactor(maturity)+ "\t" + forwardCurve.getForward(model, maturity, 0.005));		}
		return model;
	}

	private static double getParSwaprate(final ForwardCurve forwardCurve, final DiscountCurve discountCurve, final double[] swapTenor) {
		return net.finmath.marketdata.products.Swap.getForwardSwapRate(new TimeDiscretizationFromArray(swapTenor), new TimeDiscretizationFromArray(swapTenor), forwardCurve, discountCurve);
	}
}
