package es.resultados.fft;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import ca.uol.aig.fftpack.RealDoubleFFT;




public class FFTSpectralAnalyzer extends Activity implements OnClickListener {

	////////////////////////////////////////////////////////////////////////
	///MENU {OPTIONS, TONES, EXIT}
	private static final int EDIT_ID = Menu.FIRST + 2; // boton para opciones
	private static final int CLOSE_ID = Menu.FIRST + 6;
	private static final int TONE_ID = Menu.FIRST + 4;

	///////////////////////////////////////////////////////////////
	///PREFERENCES////////////////////////////////////////////////
	// Object that allows to link with the selected preferences
	SharedPreferences prefs;
	////////////////////////////////////////////////////////////////////////

	//WakeLock type object that keeps the application awake
	protected PowerManager.WakeLock wakelock;



	RecordAudio recordTask; // recording and analysis process
	AudioRecord audioRecord; // object of the AudioReord class that allows you to pick up the sound

	Button startStopButton; // Start button and pause
	boolean started = false; // button condition



	// Audio channel configuration
	int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	static double RATE = 8000; // frequency or sampling rate


	int bufferSize = 0;  // size of the buffer according to the audio configuration
	int bufferReadResult = 0; // reading size
	int blockSize_buffer = 1024; // Default value for the buffer reading block



	// Object of the class that determines the FFT of a sample vector
	private RealDoubleFFT transformer;
	int blockSize_fft = 2048; // size of the Fourier transform


	// Frequencies of the study range associated with the instrument
	static double MIN_FREQUENCY = 50; // HZ
	static double MAX_FREQUENCY = 3000; // HZ


	// Default values for the study of harmonics

	double UMBRAL = 100; // valid amplitude threshold to take into account
	// the harmonics, depends on the size of the FFT

	int LONGTRAMA = 20; // size of the study window of the harmonics
	// It also depends on the size of the FFT

	int NUM_ARMONICOS = 6; // number of harmonics to take into account

	int REL_AMP = 8; // relation of amplitudes that the first two harmonics must have to find the note
	int REL_FREC = 4; // relationship in frequency that the first two harmonics must have to find the note

	double[] aux3;{ // auxiliary vector declaration for the study of the frame
		aux3 = new double[LONGTRAMA];} // will be the array that contains the amplitude of the harmonics

	double [] valid = new double[NUM_ARMONICOS] ; // vector that will have only the harmonics of interest
	double [] amplitudes = new double[NUM_ARMONICOS] ; // vector with the amplitudes of these harmonics of interest

	double freq_asociada = 0; // value of the frequency obtained as fundamental after studying the harmonics


	// Reference frequency associated with note LA-4
	static double frec_ref = 440;

	// Array with the chromatic scale
	String[] escala = { "F#", "G", "G#", "A", "Bb", "B", "C", "C#", "D", "Eb", "E", "F" };


	// * Here we will find the audible range for a possible reuse of the code
	// although in practice we only study the spectrum in a smaller range, from 50 to 4000 Hz, for example.
	int n = 66; // index corresponding to the maximum frequency: 440 * 2 ^ (66/12) = 19912.127 Hz
	static double g = -51, j; // Index corresponding to the minimum frequency: 440 * 2 ^ (- 51/12) = 23.125 Hz
	int fin = n + (int)Math.abs(g) ; // Index corresponding to the minimum frequency: 440 * 2 ^ (- 51/12) = 23.125 Hz...

	// Array with the notes associated with the frequency array
	String a; //variable of type string of characters for the note
	String[] G; // set of possible notes


	double[] F;{ // array con todo el conjunto de frecuencias posibles  detro del rango audible
		F = new double[fin];
		G = new String[fin];
		j = g;

		// Bucle para inicializar tanto el array de frecuencias como el de notas
		for (int i = 0; i < F.length; i++) {
			j = j + 1;
			F[i] = frec_ref * Math.pow(2, j / 12);
			a = escala[i % 12];
			G[i] = a;
		}}




	// Elementos para la representacion en pantalla
	int alturaGrafica = 200; // tamaño vertical de la grafica

	int blockSize_grafica = 724; // tamaño horizontal de la grafica

	// Calculamos el cociente de la Relacion de Aspecto que usaremos para ubicar
	// todo aquello cuya posicion varie en funcion de un valor determinado
	int factor = (int) Math.round((double)blockSize_grafica/(double)alturaGrafica); //adptativo

	// Tamaños de texto para los diferentes mensajes y resultados
	int TAM_TEXT = 40;
	int TAM_TEXT1 = 10*factor;
	int TAM_TEXT2 = 5*factor;
	int TAM_TEXT3 = 7*factor;


	TextView statusText; // objeto de la clase TextView para mostrar mensaje

	ImageView imageView; // imagen para la representacion del espectro
	Bitmap bitmap;
	Canvas canvas;
	Paint paint;

	ImageView imageView2; // imagen para dibujar las bandas de frecuencia
	Bitmap bitmap2;
	Canvas canvas2;
	Paint paint2;

	Canvas canvas3;// para dibujar el valor de la SNR
	Paint paint3;

	Canvas canvas4; // para dibujar texto (frecuencia) en el espectrograma
	Paint paint4;

	Canvas canvas5; // para dibujar el average de la magnitud de los armonicos en el espectrograma
	Paint paint5;

	Canvas canvas6; // para dibujar el umbral establecido por el usuario
	Paint paint6;


	/// PREFERENCIAS

	boolean AUTODETECCION = false;

	int altura_umbral = 7;

	// Usamos la clase DecimalFormat para establecer el numero de decimales del resultado
	DecimalFormat df1;
	DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);{
		symbols.setDecimalSeparator('.');
		df1= new DecimalFormat("#.#",symbols);}

	// Cuando la actividad es llamada por primera vez
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.graficas);

		// Inicializacion de todos los elementos graficos
		statusText = (TextView) this.findViewById(R.id.StatusTextView);
		startStopButton = (Button) this.findViewById(R.id.StartStopButton);
		startStopButton.setOnClickListener(this);


		// imagen para la representacion del espectro
		imageView = (ImageView) this.findViewById(R.id.ImageView01);
		bitmap = Bitmap.createBitmap((int) blockSize_grafica, (int) alturaGrafica,
				Bitmap.Config.ARGB_8888);
		canvas = new Canvas(bitmap);
		paint = new Paint();
		paint.setColor(Color.GREEN);
		imageView.setImageBitmap(bitmap);

		// imagen para dibujar las bandas de frecuencia
		imageView2 = (ImageView) this.findViewById(R.id.ImageView02);
		bitmap2 = Bitmap.createBitmap((int) blockSize_grafica, TAM_TEXT1,
				Bitmap.Config.ARGB_8888);
		canvas2 = new Canvas(bitmap2);
		paint2 = new Paint();
		paint2.setColor(Color.WHITE);
		imageView2.setImageBitmap(bitmap2);


		// para dibujar el valor de la SNR
		canvas3 = new Canvas(bitmap);
		paint3 = new Paint();
		paint3.setColor(Color.MAGENTA);

		// para dibujar texto (frecuencia) en el espectrograma
		canvas4 = new Canvas(bitmap);
		paint4 = new Paint();
		paint4.setColor(Color.YELLOW);

		// para dibujar el average de la magnitud de los armonicos en el espectrograma
		canvas5 = new Canvas(bitmap);
		paint5 = new Paint();
		paint5.setColor(Color.RED);

		// para dibujar el umbral establecido por el usuario
		canvas6 = new Canvas(bitmap);
		paint6 = new Paint();
		paint6.setColor(Color.CYAN);


		// prevent the screen from turning off
		final PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
		this.wakelock=pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "etiqueta");
		wakelock.acquire();


		// Draw the frequency axis
		DrawEyeFrequencies();

	}

	////////////////////////////////////////////////////////////////////////a
	// Hace que la pantalla siga encendida hasta que la actividad termine
	protected void onDestroy(){
		super.onDestroy();

		this.wakelock.release();

	}

	// Additionally, it is recommended to use onResume, and onSaveInstanceState, so that,
	// if we minimize the application, the screen will turn off normally,
	// contrary, the screen will not turn off even if we do not have our application
	// spotlight.

	protected void onResume(){
		super.onResume();

		wakelock.acquire();

		// Value shown by the button when returning to activity
		startStopButton.setText("ON");


		// We load the preferences in case the user has made some modification
		// in the configuration of the application
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// Preference that allows the application to stop automatically
		// when detecting a sound with a certain volume and variance
		AUTODETECCION = prefs.getBoolean("Default_Option", false);


		// Set the detection threshold
		// If the empirical threshold is below the minimum of 98 = 7 * 14
		// We set 80 as a stop. It would correspond to a selection
		// in the seekbar less than number 7
		if(prefs.getInt("umbralPref",7)<7){
			UMBRAL = 80;
		}
		else{
			UMBRAL = 14*(prefs.getInt("umbralPref",7));
		}

		// Set the length of the detection frame
		// If the length of the frame is less than the minimum, 13,
		// We set 13 as a stop. It would correspond to a selection
		// in the seekbar less than number 13
	        /*if(prefs.getInt("longtramaPref",13)<13){
	        	LONGTRAMA = 13;
	        }
	        else{
	        	 LONGTRAMA = (prefs.getInt("longtramaPref",13));
	        }*/

		// Height that will have the line that represents the threshold of detection of harmonics
		altura_umbral = (prefs.getInt("umbralPref",7));


		//RATE = Integer.parseInt(prefs.getString("list_frec_muest","8000"));
		//blockSize_fft = Integer.parseInt(prefs.getString("list_fft","1024"));


	}

	// If you leave the activity unexpectedly
	@Override
	protected void onPause() {
		super.onPause();
		if(started) {

			started = false;
		}
	}

	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
		this.wakelock.release();

	}


	// PROCESS OR ASYNCHRONOUS TASK THAT WILL BE ABLE TO COLLECT AND ANALYZE THE ENTRY AUDIO SIGNAL
	private class RecordAudio extends AsyncTask<Void, short[], Void> {

		@Override
		protected Void doInBackground(Void... params) {
			try {

				// estimation of the size of the buffer depending on the audio configuration
				bufferSize = AudioRecord.getMinBufferSize((int)RATE,
						channelConfiguration, audioEncoding);

				// initialization of the object of the AudioRecord class
				AudioRecord audioRecord = new AudioRecord(
						MediaRecorder.AudioSource.MIC,(int) RATE,
						channelConfiguration, audioEncoding, bufferSize);

				// declaration of the vector that will first store the data collected from the microphone
				short[] audio_data = new short[blockSize_buffer]; // tipo de dato short (2^15 = 32768)

				audioRecord.startRecording(); // start recording

				while (started) { // as long as the button is not pressed again

					// reading size
					bufferReadResult = audioRecord.read(audio_data, 0, blockSize_buffer);


					// the collected samples are sent for processing
					publishProgress(audio_data);

				}

				audioRecord.stop(); // for recording momentarily

			} catch (Throwable t) { // in case of error, eg audio capture already active
				Log.e("AudioRecord", "Recording Failed");
			}

			return null;
		}

		protected void onProgressUpdate(short[]... toTransform) {



			double maximo = 0,promedio = 0, varianza = 0;

			// Arrays with audio samples in time and frequency in double format
			double[] plot, plot_spectrum;
			plot = new double[blockSize_fft];

			// initialize the vector that will contain the FFT of
			transformer = new RealDoubleFFT(blockSize_fft);


			for (int i = 0; i < bufferReadResult; i++) {

				plot[i * 2] = (double) toTransform[0][i];
				plot[i * 2 + 1] = 0; // we will increase the frequency resolution of the transform by interpolating zeros

			}

			maximo = max(plot,0,plot.length).valor;

			//average = average(plot);

			// we normalize the sound frame by dividing all the samples by the one with the highest value
			normalize(plot);

			varianza = variance(plot);

			/////////////////////////////////////////////////////////////////////////////////////
			// AUTODETECCION
			if (AUTODETECCION){
				if(valid[1]!=0){ // If harmonics have appeared
					if((maximo>=800)&&(varianza>0.04)){

						started = false;
						startStopButton.setText("ON");
						recordTask.cancel(true);

					}
				}
			}

			// We get precision with the windowing
			// Filter the harmonics in the spectrum
			// Highlights and enhances the fundamentals

			plot = applyHamming(plot);


			// Domain transformed. Perform the FFT of the frame
			transformer.ft(plot);

			statusText.setTextSize(TAM_TEXT); // we define the size for the text


			ReturnsNote(plot); // writes the resulting note on the screen

			if(freq_asociada>MIN_FREQUENCY){

				int position = CalculateIndex(freq_asociada);
				statusText.setText(SearchNote(position)+" (" + df1.format(freq_asociada)+" Hz)");
			}


			DrawEyeFrequencies(); // Draw the bands that make up the frequency axis


			// Normalize the spectrum for its representation
			plot_spectrum = normalize(plot);


			DrawSpectrum(plot_spectrum); // graphically represents the spectrum of the signal

			// Draw a red line that represents the average of the spectrum
			//canvas5.drawLine(0, heightGraphic - (float) average (frame_spectrum) * heightGraphic,
			// blockSize_grafica, alturaGrafica - (float) average (plot_spectro) * alturaGrafica, paint5);

			// Draw line cyan with the threshold selected by the user
			canvas6.drawLine(0, alturaGrafica - altura_umbral, blockSize_grafica,alturaGrafica -altura_umbral, paint6);


			WriteArmonics();

		}
	}

	public void WriteArmonics(){


		paint4.setAntiAlias(true);
		paint4.setFilterBitmap(true);
		paint4.setTextSize(TAM_TEXT2);




		for(int num = 0; num<NUM_ARMONICOS;num++){

			if(valid[num]!=0){
				canvas4.drawText(df1.format(valid[num])+ "["+ SearchNote(CalculateIndex(valid[num]))+"]",120*num,25, paint4);

			}

		}


	}

	///////////////////////////////////////////////////////////////////////////////
	// DRAW THE AXIS OF FREQUENCIES ///////////////////////////////////////////////
	public void DrawEyeFrequencies(){


		canvas2.drawColor(Color.BLACK);
		paint2.setAntiAlias(true);
		paint2.setFilterBitmap(true);

		// Valores que se mostrara en el eje X
		int[]bandas ={220,440,880,1320,1760,2350};
		paint2.setStrokeWidth(5);
		canvas2.drawLine(0,0,blockSize_grafica,0,paint2);

		paint2.setTextSize(TAM_TEXT3);
		canvas2.drawText(String.valueOf(bandas[0]),bandas[0]/factor-(TAM_TEXT3)/2,TAM_TEXT3,paint2);
		canvas2.drawText(String.valueOf(bandas[1]),bandas[1]/factor-(TAM_TEXT3)/2,TAM_TEXT3,paint2);
		canvas2.drawText(String.valueOf(bandas[2]),bandas[2]/factor-(TAM_TEXT3)/2,TAM_TEXT3,paint2);
		canvas2.drawText(String.valueOf(bandas[3]),bandas[3]/factor-(TAM_TEXT3)/2,TAM_TEXT3,paint2);
		canvas2.drawText(String.valueOf(bandas[4]),bandas[4]/factor-(TAM_TEXT3)/2,TAM_TEXT3,paint2);
		canvas2.drawText(String.valueOf(bandas[5]),bandas[5]/factor-(TAM_TEXT3)/2,TAM_TEXT3,paint2);
		canvas2.drawText("Hz",blockSize_grafica - TAM_TEXT1,TAM_TEXT3,paint2);

		imageView2.invalidate();


	}


	///////////////////////////////////////////////////////////////////////////////
	//DRAW THE SPECTRUM///////////////////////////////////////////////////////////
	public void DrawSpectrum(double[] plot_spectrum){

		// Clause of the signal to noise ratio (dB)
		// It results from the quotient between the maximum value of the spectrum between the average
		// Ideally, the SNR is worth infinity, which means that there is no noise
		// double snr2 = 10 * Math.log10 (max (spec_string, 0, spec_string.length) .value / average (spec_strict));

		canvas.drawColor(Color.BLACK);

		for (int i = 0; i < plot_spectrum.length; i++) {
			int x = i;
			int downy = (int) (alturaGrafica - (plot_spectrum[i]*alturaGrafica));
			int upy = alturaGrafica;

			canvas.drawLine(x, downy, x, upy, paint);

		}

		//paint3.setAntiAlias(true);
		//paint3.setFilterBitmap(true);
		//paint3.setTextSize(TAM_TEXT2);
		//canvas3.drawText(" SNR: " + df1.format(snr2) + " dB", blockSize_grafica-alTuraGrafica, TAM_TEXT3, paint3);

		imageView.invalidate();

	}


	// Algorithm that once detects the harmonics establishes which are the valid
	// to determine the fundamental note of the frame that is passed as input
	public String ReturnsNote(double[] plot){

		freq_asociada = 0;

		String nota_final; // cadena que contendra el valor final de la nota

		double [] armonicos = returnsArmonics(plot); // vector con los armonicos detectados

		String [] notas = new String[NUM_ARMONICOS]; // vector con las notas correspondientes a los armonicos valid

		// vector of integers with the correspondence to the position within the "scale" array with the notes
		// of the harmonics valid
		int [] indices = new int [NUM_ARMONICOS];

		// Inicializamos a 0 el vector de armonicos valid
		for(int k = 0; k<NUM_ARMONICOS;k++){
			valid[k] = 0;
		}

		int m = 0, n= 0; // indices para recorrer el array armonicos
		// We go through the vector of harmonics looking for the candidates
		while((m<NUM_ARMONICOS)&&(n<armonicos.length-1)){

			// Evitamos que se repita mas de una vez un mismo armonico y que aparezcan dos muy proximos
			// Desventaja: puede que de dos muy proximos no tomemos el de mayor amplitud


			// Si lo que viene luego en la posicion correspondiente al vector armonicos es distinto a lo que hay ahora
			if((armonicos[n+1]!=armonicos[n])){

				// Si la diferencia de distancia en frecuencia entre lo que viene luego y lo que tengo ahora
				// es menor que la LONGITUD de TRAMA mejor quedate con lo que viene luego
				//if(Math.abs(armonicos[n+1]-armonicos[n])<LONGTRAMA/2){
				if(Math.abs(armonicos[n+1]-armonicos[n])<LONGTRAMA){

					valid[m] = armonicos[n+1];

					amplitudes[m] = aux3[n+1];

					notas[m] = SearchNote(CalculateIndex(armonicos[n+1])); // calcula la nota en funcion de la frecuencia

					// devuelve el indice correspondiente a la posicion que ocupa la nota en el array "escala"
					indices[m] = ReturnPosition(SearchNote(CalculateIndex(armonicos[n+1])));

					m = m + 1; // avanzamos una posicion en "valid"
					n = n + 2; // avanzamos dos en "armonicos"

				}

				// Si lo que viene luego en el vector armonicos supera el rango de LONGTRAMA
				else{
					valid[m] = armonicos[n];

					amplitudes[m] = aux3[n];

					notas[m] = SearchNote(CalculateIndex(armonicos[n])); // calcula la nota en funcion de la frecuencia

					// devuelve el indice correspondiente a la posicion que ocupa la nota en el array "escala"
					indices[m] = ReturnPosition(SearchNote(CalculateIndex(armonicos[n])));

					m++;
					n++;

				}

			}

			else{
				n++;
			}
		}


		// Variables que contendran los valores
		// maximo y minimo del espectro asi como su relacion.
		double Min,Max,May,Men,relacion_amp,relacion_frec;



		// Asigna amplitud menor y mayor
		if(amplitudes[1]>amplitudes[0]){
			Min = amplitudes[0];
			Max = amplitudes[1];
		}
		else{
			Min = amplitudes[1];
			Max = amplitudes[0];
		}

		// Asigna frecuencia menor y mayor
		if(valid[1]> valid[0]){
			Men = valid[0];
			May = valid[1];
		}
		else{
			Men = valid[1];
			May = valid[0];
		}

		relacion_amp = Max/Min; // relacion entre las amplitudes

		relacion_frec = May/Men; // relacion entre las frecuencias

		// Si la relacion entre las amplitudes y entre las frecuencias es muy grande
		// despreciamos los armonicos y calculamos directamente la nota como
		// la correspondiente al armonico de mayor amplitud
		if((relacion_amp>REL_AMP)&&(relacion_frec>REL_FREC)){

			freq_asociada = returnsPitch(plot);
			nota_final = SearchNote(CalculateIndex(freq_asociada));

			//nota_final = ReturnsNote(CalculateIndex(returnsPitch(plot)));
		}

		else{

			// La diferencia entre los indices de ambos armonicos siempre ha de guardar
			// una cantidad de 3 unidades
			// Comprobamos que valid[1]!=0,es decir no hay armonico, para no confundir la nota
			// Fa# (escala[0]) presente en acordes como B = 5+9+0 ó D = 8+3+0
			if((indices[1]>=0)&&(Math.abs(indices[0]-indices[1])>=3)&&(valid[1]!=0)){

				int menor,mayor; //

				// comprueba que indice es menor y cual mayor
				// para pasarselos como entrada al algoritmo
				// que estima la nota en funcion de las componentes
				if(indices[1]>indices[0]){
					menor = indices[0];
					mayor = indices[1];
				}
				else{
					menor = indices[1];
					mayor = indices[0];
				}

				// Tenemos dos indices de armonicos que guardan una distancia suficiente para formar una nota
				nota_final = DeterminaNote(menor,mayor,indices[0]);
				/// Determinar freq_asociada
				// Si la nota esta en el acorde
				if(nota_final==escala[indices[0]]){
					freq_asociada = valid[1]/3;
				}
				else if(nota_final==escala[indices[1]]){
					freq_asociada = valid[0]/3;
				}
				else{
					freq_asociada = valid[0]/3;
				}
				// coge la frecuencia de la otra nota y la divide entre 3
				// Si no coge valid[0] y lo divide entre 3

			}

			else if(indices[0]-indices[1] == 0){// si tenemos dos veces la misma nota
				nota_final = notas[0]; // sera esta la que prevalezca
				freq_asociada = valid[0];
			}
			else{ // si no cumple ninguno de estos requisitos suponemos que es la de mayor amplitud

				freq_asociada = returnsPitch(plot);
				nota_final = SearchNote(CalculateIndex(freq_asociada));

				//nota_final = ReturnsNote(CalculateIndex(returnsPitch(plot)));
			}


		}

		return nota_final;
	}

	// Method that returns the note depending on the presence of the components of the major chord of that note
	public String DeterminaNote(int menor, int mayor, int defecto){

		// Chromatic scale and the numeric values of the notes as chords
    	/* F# = [0,4,7]  = [F#,Bb,C#]
    	 * G  = [1,5,8]  = [G,B,D]
    	 * G# = [2,6,9]  = [G#,C,Eb]
    	 * A  = [3,7,10] = [A,C#,E]
    	 * Bb = [4,8,11] = [Bb,D,F]
    	 * B  = [5,9,0]  = [B,Eb,F#]
    	 * C  = [6,10,1] = [C,E,G]
    	 * C# = [7,11,2] = [C#,F,G#]
    	 * D  = [8,0,3]  = [D,F#,A]
    	 * Eb = [9,1,4]  = [Eb,G,Bb]
    	 * E  = [10,2,5] = [E,G#,B]
    	 * F  = [11,3,6] = [F,A,C] */

		// String that will return as estimated note
		String nota = escala[defecto]; // by default is the one that is first in the array of harmonics

		// We will be discarding possibilities having ordered from lower to higher indices
		// We start with the minor that is 0, the higher the lower index, the less likely
		// will have to keep a ratio of 3 units with the highest index

		if(menor==0){

			if((mayor == 4)||(mayor==7)){

				nota = escala[0]; // es LA
			}
			else if((mayor == 5)||(mayor==9)){
				nota = escala[5];
			}
			else if ((mayor == 3)||(mayor==8)){
				nota = escala[8]; // es RE
			}


		}
		else if(menor==1){


			if((mayor == 5)||(mayor==8)){

				nota = escala[1]; // es SOL
			}
			else if((mayor == 6)||(mayor==10)){

				nota = escala[6];
			}
			else if ((mayor == 4)||(mayor==9)){

				nota = escala[9]; // es MIb

				//freq_asociada = valid[0]/3;
			}

		}
		else if(menor==2){

			if((mayor == 6)||(mayor==9)){

				nota = escala[2]; // es 2
			}
			else if((mayor == 7)||(mayor==11)){
				nota = escala[7];
			}
			else if ((mayor == 5)||(mayor==10)){

				nota = escala[10];// es MI

				//freq_asociada = valid[0]/3;
			}


		}
		else if(menor==3){

			if((mayor == 7)||(mayor==10)){

				nota = escala[3]; // es LA
			}
			else if((mayor == 6)||(mayor==11)){

				nota = escala[11]; // es FA
			}

			else if (mayor==8){

				nota = escala[8];// es RE

				//freq_asociada = valid[1]/3;
			}

		}
		else if(menor==4){

			if((mayor == 8)||(mayor==11)){

				nota = escala[4]; // es 4
			}

			else if (mayor==7){

				nota = escala[0];

			}
			else if(mayor==9){

				nota = escala[9]; // es MIb

				//freq_asociada = valid[1]/3;
			}
		}

		else if(menor==5){

			if(mayor==8){
				nota = escala[1];
			}
			else if(mayor==9){
				nota = escala[5]; // es 5
			}
			else if(mayor==10){

				nota = escala[10]; // es MI

				//freq_asociada = valid[1]/3;
			}
		}
		else if(menor==6){

			if(mayor==9){
				nota = escala[2];
			}
			else if(mayor==10){
				nota = escala[6]; // es 6
			}
			else if(mayor==11){
				nota = escala[11];
			}


		}
		else if(menor==7){

			if(mayor==10){
				nota = escala[3];
			}
			else if(mayor==11){
				nota = escala[7]; // es 7
			}

		}
		else if(menor==8){
			if(mayor==11){
				nota = escala[4];
			}
		}

		else {

			nota =  "FALLO";

		}
		return nota;
	}


// Function responsible for returning the position it occupies
// in frequency arrays and notes

	public int CalculateIndex(double pitch){

		double num; // indice correspondiente a la posicion respecto al LA4
		// (sera el valor que redondearemos para obtener 'indice')

		int indice; // valor redondeado de num


		// La siguiente operacion devuelve el indice correspondiente a la frecuencia
		// detectada, es la operacion inversa a la utilizada para calcular la teorica
		num = 12 * Math.log10(pitch / frec_ref) / Math.log10(2) + 51;

		indice = (int) Math.round(num); // convierte el indice a entero

		return indice;

	}

	// Function responsible for returning the theoretical frequency

	public double ReturnsFrequency(int indice){


		// OBSERVATION: CORRECTION OF A POSITION F [num-1]
		// TO RETURN THE IDEAL FREC CORRECTLY
		// the frequency will be the value corresponding to the position
		// 'index' in the array 'F' of the range of possible frequencies

		return F[indice - 1];

	}

	// Function responsible for returning the corresponding note

	public String SearchNote(int indice){

		// the note will be the value corresponding to the position
		// 'index' in the array 'G' of the range of possible notes
		return G[indice];

	}


	// Function responsible for returning the index
	// or position of the note in the "scale" array

	public int ReturnPosition(String nota_draw){


		int posicion = 0;
		boolean cumple = false;

		while((!cumple) && (posicion<escala.length)){
			if(nota_draw == escala[posicion]){
				cumple = true;
			}
			else{
				posicion ++;
			}

		}

		return posicion;

	}



	// Algorithm that receives as input parameter a frequency frame and determines which is the
	// main harmonic, that is, the largest
	public double returnsPitch(double[] data) {


		// index or position in which we start reading in the spectrum frame
		// which corresponds to the minimum frequency of the detection range
		final int min_frequency_fft = (int) Math.round(MIN_FREQUENCY
				* blockSize_buffer / RATE);
		// index or position in which we have just read in the spectrum frame
		// which corresponds to the maximum frequency of the detection range
		final int max_frequency_fft = (int) Math.round(MAX_FREQUENCY
				* blockSize_buffer / RATE);
		double best_frequency = min_frequency_fft; // initialize the candidate frequency in the minimum
		double best_amplitude = 0; // initialize to 0 the amplitude that in the end will be the maximum of the plot

		// we go through the plot
		for (int i = min_frequency_fft; i <= max_frequency_fft; i++) {

			// calculate the current frequency by restoring the value of the index or posicon
			final double current_frequency = i * 1.0 * RATE
					/(blockSize_buffer);


			//final double normalized_amplitude = Math.abs(data[i]);

			// calculate the current amplitude
			final double current_amplitude = Math.pow(data[i * 2], 2)
					+ Math.pow(data[i * 2 + 1], 2);

			// normalize the current amplitude
			final double normalized_amplitude = current_amplitude
					* Math.pow(MIN_FREQUENCY * MAX_FREQUENCY, 0.5)
					/ current_frequency;

			// if it is greater than the previous one it is a candidate to be the maximum
			if (normalized_amplitude > best_amplitude) {
				best_frequency = current_frequency;
				best_amplitude = normalized_amplitude;
			}
		}
		return best_frequency;

	}


	// Algorithm that receives as input parameter a frequency frame and determines which are the
	// main harmonics that make up the signal according to the criterion of comparison with a THRESHOLD
	public double[] returnsArmonics(double[] data) {

		int r = 0; // indice para el recorrido del vector con los armonicos

		int umbral = (int)UMBRAL; // condicion necesaria para considerarse armonico
		int longtrama = LONGTRAMA; // longitud de la trama para el estudio de los armonicos


		// indice o poscion en el que empezaremos a leer en la trama del espectro
		// que se corresponde con la frecuencia mínima del rango de deteccion
		final int min_frequency_fft = (int) Math.round(MIN_FREQUENCY
				* blockSize_buffer / RATE);

		// indice o poscion en el que acabaremos de leer en la trama del espectro
		// que se corresponde con la frecuencia máxima del rango de deteccion
		final int max_frequency_fft = (int) Math.round(MAX_FREQUENCY
				* blockSize_buffer / RATE);
		double best_frequency = min_frequency_fft; // inicializamos la frecuencia candidata en el minimo

		double best_amplitude = 0; // inicializamos la amplitud a comparar con el umbral a 0

		double[] aux2; // declaracion de vector auxiliar para el estudio de la trama
		aux2 = new double[longtrama]; // sera el array que contenga los armonicos


		// loop that travels to the position of the maximum range [MIN_FREQUENCY, MAX_FREQUENCY]
		for (int i = min_frequency_fft; i< max_frequency_fft; i = i + longtrama) {

			best_amplitude = 0; // restauramos el valor de la amplitud maxima una vez leida la trama

			// loop that runs through the frame
			for (int j = 0; j < longtrama; j++) {


				final double current_frequency = (i+j) * 1.0 * RATE
						/(blockSize_buffer);

				//final double normalized_amplitude = Math.abs(data[i+j]);

				final double current_amplitude = Math.pow(data[(i+j) * 2], 2)
						+ Math.pow(data[(i+j) * 2 + 1], 2);
				final double normalized_amplitude = current_amplitude
						* Math.pow(MIN_FREQUENCY * MAX_FREQUENCY, 0.5)
						/ current_frequency;

				if (normalized_amplitude > best_amplitude) {
					best_frequency = current_frequency;
					best_amplitude = normalized_amplitude;
				}
			}

			if(best_amplitude>umbral){
				// store the frequency position in aux2
				// that meets the 'threshold' requirement
				// and in aux3 the corresponding amplitude

				aux3[r] = best_amplitude;
				aux2[r] = best_frequency;
				r = r + 1;


			}

		}
		return aux2; // devuelve el vector con las frecuencias de los armonicos

	}

	// Method for calculating the average of a sample vector.

	private static double average(double[] datos) {

		int N = datos.length;
		double med = 0;
		for (int k = 0; k < N; k++) {

			med += Math.abs(datos[k]);
		}
		med = med / N;
		return med;
	}

	// Method for calculating the mean of a sample vector.

	private static double media(double[] datos) {
		// Computo de la media.
		int N = datos.length;
		double med = 0;
		for (int k = 0; k < N; k++) {

			med += datos[k];
		}
		med = med / N;
		return med;
	}

	// Method for calculating the variance of a sample vector.

	private static double variance(double[] datos) {
		// Computo de la media.
		int N = datos.length;
		double med = media(datos);
		// Computo de la variance.
		double varianza = 0;
		for (int k = 0; k < N; k++) {
			varianza += Math.pow(datos[k] - med, 2);
		}
		varianza = varianza / (N - 1);
		return varianza;
	}


	// Method for the normalization of a sample vector.

	private static double[] normalize(double[] datos) {

		double maximo = 0;
		for (int k = 0; k < datos.length; k++) {
			if (Math.abs(datos[k]) > maximo) {
				maximo = Math.abs(datos[k]);
			}
		}
		for (int k = 0; k < datos.length; k++) {
			datos[k] = datos[k] / maximo;
		}
		return datos;
	}



	// Method to blind Hamming a vector of samples.

	private static double[] applyHamming(double[] datos) {
		double A0 = 0.53836;
		double A1 = 0.46164;
		int Nbf = datos.length;
		for (int k = 0; k < Nbf; k++) {
			datos[k] = datos[k] * (A0 - A1 * Math.cos(2 * Math.PI * k / (Nbf - 1)));
		}
		return datos;
	}




	// Function that returns an object of the Maximum class, which contains:
	// maximum value and position in the frame that is passed as a parameter.
	// Tickets:
	// - x = frame or array to analyze
	// - ini = start of the frame
	// - end = end of the frame
	// Departure:
	// - Maximum: object of the Maximum class that contains (value, position)
	// of the maximum of the plot
	public Maximum max(double[] x, int ini, int fin) {

		Maximum miMaximo;
		miMaximo = new Maximum();

		for (int i = ini; i < fin; i++) {
			if (Math.abs(x[i]) >= miMaximo.valor) {
				miMaximo.valor = Math.abs(x[i]);
				miMaximo.position = i;
			}

		}

		return miMaximo;

	}

	// Definition of the object class Maximum
	class Maximum {
		int position;// posicion
		double valor;
	}


	public void onClick(View v) {
		if (started) {
			started = false;
			startStopButton.setText("ON");
			recordTask.cancel(true);
			valid[1]=0;
		} else {
			started = true;
			startStopButton.setText("OFF");
			recordTask = new RecordAudio();
			recordTask.execute();
		}
	}
}