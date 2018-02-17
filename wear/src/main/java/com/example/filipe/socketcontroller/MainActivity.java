package com.example.filipe.socketcontroller;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.view.drawer.WearableActionDrawer;
import android.support.wearable.view.drawer.WearableNavigationDrawer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import com.example.filipe.socketcontroller.charts.DynamicLineChart;
import com.example.filipe.socketcontroller.charts.DynamicPieChart;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.eazegraph.lib.charts.BarChart;
import org.eazegraph.lib.models.BarModel;

import java.util.Timer;
import java.util.TimerTask;

import static com.example.filipe.socketcontroller.util.UI.fitToScreen;
import static com.example.filipe.socketcontroller.util.UI.hide;
import static com.example.filipe.socketcontroller.util.UI.isVisible;
import static com.example.filipe.socketcontroller.util.UI.toast;
import static com.example.filipe.socketcontroller.util.UI.unhide;


public class MainActivity extends Activity implements MessageApi.MessageListener, SensorEventListener , GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
{
    private static final String TAG = "Main Activity Watch";

    /* ***** */
    /* ????? */
    /* ***** */
    private int Primeiroconsumo;
    private int consumo;
    private int primeiro;
    private int consumoTotal;
    private int count = 0;
    private boolean changedStart;
    private boolean changedEnd;
    private Timer timer;
    private TimerTask checkSecond;
    //Done by Pedro to implement the schedule service
    private Button _buttonStart;
    private Button _buttonEnd;
    private int seconds;

    /* **************** */
    /* BACK-END (GERAL) */
    /* **************** */
    private PowerManager.WakeLock cpuWakeLock;
    private PushThread pushThread;
    private long _last_push;
    private long _sampling_diff = 40;        // alterei o sampling rate aqui

    /* ***************** */
    /* BACK-END (SENSOR) */
    /* ***************** */
    private float[] _rotationMatrix = new float[16];
    private float x;
    private float z;
    //private float _orientationVals[]={0,0,0};
    private boolean _sensor_running = false;
    private SensorManager _sensorManager;
    private Sensor _sensor;

    /* ****************** */
    /* BACK-END (CONEXÃO) */
    /* ****************** */
    private GoogleApiClient _client;
    private Node _phone; // the connected device to send the message to
    //private int _count=0;
    public static final String WEAR_ACC_SERVICE = "acc";

    /* **************** */
    /* NAVEGAÇÃO E AÇÃO */
    /* **************** */
    private WearableNavigationDrawer navigationDrawer;
    private WearableActionDrawer actionDrawer;
    private Menu actionMenu;
    private MenuInflater menuInflater;
    private Tab[] tabs;
    private static final int NONE = -1;
    private View globalView;
    private View[] tabViews;

    /* ************** */
    /* START/STOP TAB */
    /* ************** */
    private TextView textSensorState;
    private int _factor;
    private CheckBox _leftHanded;

    /* ************ */
    /* SCHEDULE TAB */
    /* ************ */
    private Button _buttonSchedule;
    private TextView _StartTime;
    private TextView _EndTime;
    private static final int SELECT_TIME_START = 0;
    private static final int SELECT_TIME_END = 1;
    private static final int TIME_CONFIRMED = 2;
    private int scheduleState = 0;
    private TimePicker InitialTime;
    private TimePicker EndTime;
    private LinearLayout chooseStartTime;
    private LinearLayout chooseEndTime;

    /* ***** */
    /* STATS */
    /* ***** */
    private DynamicPieChart piePessoas;
    private DynamicPieChart pieEnergias;
    private DynamicPieChart piePlugsAcum;
    //private PlugPieChartValues piePlugsAcumValues;
    private BarChart mBarChart;
    private DynamicLineChart lineChartPlugs;

    /* *** */
    /* LOG */
    /* *** */
    private TextView _x_acc;
    private TextView _y_acc;
    private TextView _z_acc;
    private TextView _tms;
    private TextView _consumo;

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* ****************** */
    /* ANDROID LIFE-CYCLE */
    /* ****************** */

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.general_layout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // A View global (que contém as outras "subviews") é criada
        globalView = findViewById(R.id.bosta);

        // As "subviews" são armazenadas num vetor
        tabViews = new View[WattappTabConfig.values().length];
        for(WattappTabConfig config : WattappTabConfig.values())
        { tabViews[config.ordinal()] = globalView.findViewById(config.id); }

        // São obtidos os IDs dos elementos da View e os elementos são configurados
        setupViewElements();

        actionDrawer = (WearableActionDrawer) findViewById(R.id.bottom_action_drawer);
        actionMenu = actionDrawer.getMenu();
        actionDrawer.lockDrawerClosed();
        menuInflater = getMenuInflater();

        // Possibilita a navegação pelos tabs presentes no TabAdapter
        navigationDrawer = (WearableNavigationDrawer) findViewById(R.id.top_navigation_drawer);
        navigationDrawer.setAdapter(new TabAdapter(this));

        drawTab(WattappTabConfig.DEFAULT);

        seconds = 0;
        Primeiroconsumo=0;
        consumo = 0;
        primeiro = 0;
        consumoTotal = 0;

        _sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        _sensor = _sensorManager.getDefaultSensor( Sensor.TYPE_ORIENTATION);
        _last_push = System.currentTimeMillis();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    @Override
    public void onConnectionSuspended(int i) { }

    @Override
    public void onStart()
    { super.onStart(); }

    @Override
    protected void onStop()
    {
        super.onStop();
        Wearable.MessageApi.removeListener(_client, this);
        _client.disconnect();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        new Thread(()->
        {
            _client = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            _client.connect();
            Wearable.MessageApi.addListener(_client, this);


            Log.i(TAG, "On resume called");

            PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            cpuWakeLock.acquire();

            // _sensorManager.registerListener(this, _sensor, SensorManager.SENSOR_DELAY_UI);
        }).start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        //Log.wtf(TAG,event.toString());

/*
        SensorManager.getRotationMatrixFromVector(_rotationMatrix,
                event.values);
        SensorManager
                .remapCoordinateSystem(_rotationMatrix,
                        SensorManager.AXIS_X, SensorManager.AXIS_Z,
                        _rotationMatrix);
        SensorManager.getOrientation(_rotationMatrix, _orientationVals);

        // Optionally convert the result from radians to degrees
        _orientationVals[0] = (float) Math.toDegrees(_orientationVals[0]);
        _orientationVals[1] = (float) Math.toDegrees(_orientationVals[1]);
        _orientationVals[2] = (float) Math.toDegrees(_orientationVals[2]);

//        Yaw:  _orientationVals[0]
//        Pitch:  _orientationVals[1]
//        Roll:     _orientationVals[2]

        float x = _orientationVals[0];//event.values[0];
        float y = _orientationVals[1];//event.values[1];
        float z = _orientationVals[2];
        int val =4;*/

       // if(x>val||y>val||z>val) {
       // float y = event.values[1];





        //}
          //  Log.i(TAG,"Sending data");

          //  float[] data = {x,y};
            x = event.values[0];
           // _x_acc.setText(x+"");
            z = event.values[2];
            z = _factor*z;
           // _y_acc.setText(z+"");

//            Log.i("DEBUG",x+","+z);

    //Log.i(TAG,"sending data form watch");
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* *********************** */
    /* COMUNICAÇÃO WEAR-MOBILE */
    /* *********************** */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.NodeApi.getConnectedNodes(_client)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>()
                {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult nodes)
                    {
                        for (Node node : nodes.getNodes())
                        {
                            _phone = node;
                            toast(getApplicationContext(),"Connected to device `"+node.getDisplayName()+"`!");
                        }
                    }
                });
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    { toast(getApplicationContext(),"Connection failed! ("+connectionResult.toString()+")"); }

    private void sendMessage(String key)
    {
        if (_phone != null && _client!= null && _client.isConnected())
        {
            Wearable.MessageApi.sendMessage(
                    _client, _phone.getId(), WEAR_ACC_SERVICE + "" + key, null).setResultCallback(
                    new ResultCallback<MessageApi.SendMessageResult>()
                    {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult)
                        {
                            if (!sendMessageResult.getStatus().isSuccess())
                            {
                                Log.e(TAG, "Failed to send message with status code: "
                                        + sendMessageResult.getStatus().getStatusCode());
                            }
                            else
                            {
                                Log.d("SENDMESSAGE","MESSAGE SENT - "+key);
                                Log.d("SENDMESSAGE","status "+sendMessageResult.getStatus().isSuccess());
                            }
                        }
                    }
            );
        }
        else
        {
            Log.d("SENDMESSAGE","Failed to send a message!");
            Log.d("SENDMESSAGE","client = "+_client);
            Log.d("SENDMESSAGE","phone = "+_phone);
            Log.d("SENDMESSAGE","isConnected = "+_client.isConnected());
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        try
        {
            String [] valores = messageEvent.getPath().split("-");
            String event = valores[0];
            switch(event)
            {
                case "Power":
                    int nrPessoas = (valores.length - 1 )/ 2;
                    for(int i = 0; i < nrPessoas; i++)
                    {
                        piePessoas.setValue(valores[i*2+1], Float.parseFloat(valores[i*2+2]));
                    }
                    piePessoas.startAnimation();
                    toast(getApplicationContext(),"Power usage by person has been updated!");
                    break;

                case "Energy":
                    int tamanho = (valores.length - 1 )/ 2;
                    for(int i = 0; i < tamanho; i++)
                    {
                        pieEnergias.setValue(valores[i*2+1], Float.parseFloat(valores[i*2+2]));
                    }
                    pieEnergias.startAnimation();
                    toast(getApplicationContext(),"Energy data has been updated!");
                    break;

                case "Total power":
                    _consumo.setText(valores[1]);
                    toast(getApplicationContext(),"Total consumption has been updated!");
                    break;

                case "Plug consumption":
                    String plugName = valores[1];
                    float point = Float.parseFloat(valores[2]);
                    lineChartPlugs.addPoint(plugName,point);
                    break;

                default:
                    Log.d("MESSAGERECEIVED","Error: evento `"+event+"` desconhecido");
                    break;
            }
        }
        catch(Exception e)
        {
            Log.i("Error",messageEvent.getPath());
            e.printStackTrace();
        }
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* ** */
    /* UI */
    /* ** */

    public void showStartPicker(View v)
    {
        if(isVisible(chooseStartTime))
        { hide(chooseStartTime); }
        else
        { unhide(chooseStartTime); }
    }

    public void showEndPicker(View v)
    {
        if(isVisible(chooseEndTime))
        { hide(chooseEndTime); }
        else
        { unhide(chooseEndTime); }
    }

    public void handleSensorClick(MenuItem item)
    {
        actionDrawer.closeDrawer();

        if(!_sensor_running)
        {
            textSensorState.setText(R.string.SENSOR_ON);

            _factor = _leftHanded.isChecked()? -1 : 1;
            _sensorManager.registerListener(this, _sensor, SensorManager.SENSOR_DELAY_FASTEST);
            _sensor_running = true;
            pushThread = new PushThread();
            pushThread.start();
        }
        else
        {
            //cpuWakeLock.release();
            textSensorState.setText(R.string.SENSOR_OFF);

            _sensorManager.unregisterListener(this);
            _sensor_running = false;

            try
            { pushThread.join(); }
            catch (InterruptedException e)
            { e.printStackTrace(); }
        }
    }

    public void handleQuitClick(MenuItem item)
    {
        actionDrawer.closeDrawer();
        cpuWakeLock.release();
        _sensorManager.unregisterListener(this);
        _sensor_running = false;
        this.finish();
    }

    public void handleScheduleButton(View v)
    {
        String time = _StartTime.getText().toString()+"/";
        time += _EndTime.getText().toString();
        switch(scheduleState)
        {
            case SELECT_TIME_START:
                _buttonSchedule.setText(R.string.SET_SCHEDULE_CONFIRM_START);
                scheduleState++;
                break;

            case SELECT_TIME_END:
                _buttonSchedule.setText(R.string.SET_SCHEDULE_CONFIRM_END);
                scheduleState++;
                break;

            case TIME_CONFIRMED:
                _buttonSchedule.setText(R.string.SET_SCHEDULE);
                scheduleState = SELECT_TIME_START;
                break;

            default:
                break;
        }
        sendMessage(time);
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* ******* */
    /* THREADS */
    /* ******* */

    private class PushThread extends Thread
    {
        public void run()
        {
            while(_sensor_running)
            {
                sendMessage(x+"#"+z);
                //Log.i("DEBUG",x+"#"+z);

                try
                { Thread.sleep(_sampling_diff); }
                catch (InterruptedException e)
                { e.printStackTrace(); }
            }
        }
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /* ************************** */
    /* NAVEGAÇÃO E AÇÕES DOS TABS */
    /* ************************** */

    /* Separador da aplicação (com respetivo layout) */
    private class Tab extends Fragment
    {
        private WattappTabConfig choice;

        public Tab(final WattappTabConfig choice)
        {
            this.choice = choice;
            final Bundle arguments = new Bundle();
            arguments.putSerializable("TAB",choice);
            this.setArguments(arguments);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState)
        {
            // São retiradas as ações presentes (de outras tabs)
            actionMenu.clear();

            // Caso tenha um menu de ações
            if(choice.menu != NONE)
            {
                // É permitido que seja acedido
                actionDrawer.unlockDrawer();

                // O menu de ações é populado com as ações específicas do tab
                menuInflater.inflate(choice.menu, actionMenu);
            }
            else
            {
                // Não é permitido que seja acedido
                actionDrawer.lockDrawerClosed();
            }


            // É mostrada unicamente a View pretendida (do tab)
            // (as restantes são escondidas)
            for(int i = 0; i < tabViews.length; i++)
            {
                if(choice.ordinal() == i) unhide(tabViews[i]);
                else hide(tabViews[i]);
            }

            return globalView;
        }
    }

    /* Fornece acesso aos tabs do enum */
    private final class TabAdapter extends WearableNavigationDrawer.WearableNavigationDrawerAdapter
    {
        private final Context context;
        private WattappTabConfig currentTab = WattappTabConfig.DEFAULT;

        TabAdapter(final Context context)
        { this.context = context; }

        @Override
        public String getItemText(int index)
        { return context.getString(WattappTabConfig.values()[index].title); }

        @Override
        public Drawable getItemDrawable(int index)
        { return context.getDrawable(WattappTabConfig.values()[index].icon); }

        @Override
        public void onItemSelected(int index)
        {
            drawTab(WattappTabConfig.values()[index]);
        }

        @Override
        public int getCount()
        { return WattappTabConfig.values().length; }
    }

    private void setupViewElements()
    {
        // Start/Stop
        _leftHanded     = (CheckBox) globalView.findViewById(R.id.checkLeftHanded);
        textSensorState = (TextView) globalView.findViewById(R.id.textSensorState);

        // Schedule
        _buttonSchedule = (Button) globalView.findViewById(R.id.buttonSchedule);
        _buttonStart    = (Button) globalView.findViewById(R.id.buttonStart);
        _buttonEnd      = (Button) globalView.findViewById(R.id.buttonEnd);
        _StartTime      = (TextView) globalView.findViewById(R.id.HoraInicio);
        _EndTime        = (TextView) globalView.findViewById(R.id.HoraFim);
        chooseStartTime = (LinearLayout) globalView.findViewById(R.id.PrimeiroTempo);
        chooseEndTime   = (LinearLayout) globalView.findViewById(R.id.UltimoTempo);
        InitialTime     = (TimePicker) globalView.findViewById(R.id.InitialPicker);
        EndTime         = (TimePicker) globalView.findViewById(R.id.EndPicker);
        InitialTime.setIs24HourView(true);
        InitialTime.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener()
        {
            public void onTimeChanged(TimePicker view, int hourOfDay, int minute)
            {
                String strHour = "";
                String strMinute = "";

                if(hourOfDay < 10) strHour += "0";
                strHour += hourOfDay;

                if(minute < 10) strMinute += "0";
                strMinute += minute;

                _StartTime.setText(strHour + ":" + strMinute);
                changedStart = true;
            }
        });
        EndTime.setIs24HourView(true);
        EndTime.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener()
        {
            public void onTimeChanged(TimePicker view, int hourOfDay, int minute)
            {
                String strHour = "";
                String strMinute = "";

                if(hourOfDay < 10) strHour += "0";
                strHour += hourOfDay;

                if(minute < 10) strMinute += "0";
                strMinute += minute;

                _EndTime.setText(strHour + ":" + strMinute);
                changedEnd = true;
            }
        });
        chooseStartTime.setVisibility(LinearLayout.GONE);
        chooseEndTime.setVisibility(LinearLayout.GONE);

        // Stats
        piePessoas = (DynamicPieChart) globalView.findViewById(R.id.piePessoas);
        pieEnergias = (DynamicPieChart) globalView.findViewById(R.id.pieEnergias);
        piePlugsAcum = (DynamicPieChart) globalView.findViewById(R.id.piePlugsAcum);
        mBarChart = (BarChart) globalView.findViewById(R.id.barchart);
        lineChartPlugs = (DynamicLineChart) globalView.findViewById(R.id.linechartplugs);
        //piePlugsAcumValues = new PlugPieChartValues(piePlugsAcum);
        fitToScreen(this,piePessoas);
        fitToScreen(this,pieEnergias);
        fitToScreen(this,piePlugsAcum);
        fitToScreen(this,mBarChart);
        fitToScreen(this,lineChartPlugs);

        // Log
        _x_acc          = (TextView) globalView.findViewById(R.id.x_text_field);
        _y_acc          = (TextView) globalView.findViewById(R.id.y_text_field);
        _z_acc          = (TextView) globalView.findViewById(R.id.z_text_field);
        _tms            = (TextView) globalView.findViewById(R.id.tms_text_field);
        _consumo        = (TextView) globalView.findViewById(R.id.ConsumoInsert);

        testEazeGraph();
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    public void testEazeGraph()
    {
        /*piePessoas.addPieSlice(new PieModel("Freetime", 15, Color.parseColor("#FE6DA8")));
        piePessoas.addPieSlice(new PieModel("Sleep", 25, Color.parseColor("#56B7F1")));
        piePessoas.addPieSlice(new PieModel("Work", 35, Color.parseColor("#CDA67F")));
        piePessoas.addPieSlice(new PieModel("Eating", 9, Color.parseColor("#FED70E")));

        piePessoas.startAnimation();*/

        mBarChart.addBar(new BarModel(2.3f, 0xFF123456));
        mBarChart.addBar(new BarModel(2.f,  0xFF343456));
        mBarChart.addBar(new BarModel(3.3f, 0xFF563456));
        mBarChart.addBar(new BarModel(1.1f, 0xFF873F56));
        mBarChart.addBar(new BarModel(2.7f, 0xFF56B7F1));
        mBarChart.addBar(new BarModel(2.f,  0xFF343456));
        mBarChart.addBar(new BarModel(0.4f, 0xFF1FF4AC));
        mBarChart.addBar(new BarModel(4.f,  0xFF1BA4E6));

        mBarChart.startAnimation();

        lineChartPlugs.addPoint("plug1.local",2.4f);
        lineChartPlugs.addPoint("plug1.local",1f);
        lineChartPlugs.addPoint("plug1.local",4.4f);
        lineChartPlugs.addPoint("plug1.local",4.4f);
        lineChartPlugs.addPoint("plug1.local",4.4f);
        lineChartPlugs.addPoint("plug1.local",4.4f);
        lineChartPlugs.addPoint("plug2.local",4.4f);
        lineChartPlugs.addPoint("plug2.local",5f);
        lineChartPlugs.addPoint("plug1.local",9f);
        lineChartPlugs.startAnimation();

        piePlugsAcum.incValue("plug1.local",30);
        piePlugsAcum.incValue("plug2.local",20);
        piePlugsAcum.incValue("plug3.local",20);
        piePlugsAcum.incValue("plug1.local",20);
        piePlugsAcum.incValue("plug5.local",20);
        piePlugsAcum.startAnimation();
    }

    private void drawTab(WattappTabConfig config)
    {
        // Caso tenha um menu de ações
        if(config.menu != NONE)
        {
            // São retiradas as ações presentes (de outras tabs)
            actionMenu.clear();

            // É permitido que seja acedido
            actionDrawer.unlockDrawer();

            // O menu de ações é populado com as ações específicas do tab
            menuInflater.inflate(config.menu, actionMenu);
        }
        else
        {
            // Não é permitido que seja acedido
            actionDrawer.lockDrawerClosed();
        }

        for(int i = 0; i < tabViews.length; i++)
        {
            if(config.ordinal() == i) unhide(tabViews[i]);
            else hide(tabViews[i]);
        }
    }


}