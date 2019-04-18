package com.example.bluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button btn_encontrar, btn_enviar, btn_listar;
    ListView listView;
    TextView txt_exibe, txt_status;
    EditText txt_msgs;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;

    SendReceiver sendReceiver;

    //possiveis estados
    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    int REQUEST_ENABLE_BLUETOOTH = 1;

    private static final String APP_NAME = "BluetoothChat";
    private static final UUID MY_UUID = UUID.fromString("0f9b5e92-38cf-4733-846c-ed82bfe5f641"); //gerado automaticamente de um site que tem esse proposito

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewByIds();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //gera a intençao de utilizar o bluetooth
        if (!bluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }

        implementListeners();
    }

    private void implementListeners() {

        //aqui vai trabalhar em cima da funcionalidade do botao listar
        btn_listar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();
                String[] strings = new String[bt.size()];
                btArray = new BluetoothDevice[bt.size()];
                int index = 0;

                if (bt.size() > 0) {
                    for(BluetoothDevice device : bt){
                        btArray[index] = device;
                        strings[index] = device.getName();
                        index++;
                    }
                    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, strings);
                    listView.setAdapter(arrayAdapter);
                }
            }
        });

        btn_encontrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ServerClass serverClass = new ServerClass();
                serverClass.start();
            }
        });

        // conecta em um dos dispositivos da lista
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ClientClass clientClass = new ClientClass(btArray[position]);
                clientClass.start();

                txt_status.setText("Conectando");
            }
        });

        btn_enviar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = String.valueOf(txt_msgs.getText());
                sendReceiver.write(s.getBytes());
            }
        });
    }

    //cuida de mudar o status dependendo do evento
    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what){
                case STATE_LISTENING:
                    txt_status.setText("Aguardando");
                    break;
                case STATE_CONNECTING:
                    txt_status.setText("Conectando");
                    break;
                case STATE_CONNECTED:
                    txt_status.setText("Conectado");
                    break;
                case STATE_CONNECTION_FAILED:
                    txt_status.setText("Falha na Conexao");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuffer = (byte[]) msg.obj; //vai ler o buffer pra um array
                    String tempMsg = new String(readBuffer, 0, msg.arg1); //transformar em string
                    txt_exibe.setText(tempMsg); //apresentar na tela
                    break;
            }
            return true;
        }
    });

    //associa os botoes com as variaveis
    private void  findViewByIds(){
        btn_encontrar=(Button) findViewById(R.id.btn_encontrar);
        btn_enviar=(Button) findViewById(R.id.btn_enviar);
        btn_listar=(Button) findViewById(R.id.btn_listar);
        listView=(ListView) findViewById(R.id.lista_disps);
        txt_status=(TextView) findViewById(R.id.txt_status);
        txt_exibe=(TextView) findViewById(R.id.txt_exibe);
        txt_msgs=(EditText) findViewById(R.id.txt_msgs);
    }

    //essa classe cuida da tarefa do dispositivo como servidor
    private class ServerClass extends Thread{
        private BluetoothServerSocket serverSocket; //eh necessario um BT server socket, responsavel por "ouvir" conexoes

        public ServerClass(){
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID); //esse socket leva os parametros nome, e uuid
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            BluetoothSocket socket = null; //inicializa um BT socket

            while (socket == null){
                try {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTING;
                    handler.sendMessage(message);
                    socket = serverSocket.accept(); //se tiver null, entao esses socket recebe um socket conectado que no caso seria conectado utilizando o BT server
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }

                if(socket != null){
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTED;
                    handler.sendMessage(message);

                    sendReceiver = new SendReceiver(socket);
                    sendReceiver.start(); //se nao esta null é pq ja esta conectado, entao podemos inicializar a classe que trata de envio e recebimento de msgs

                    break;
                }
            }
        }
    }

    //classe que cuida do dispositivo como cliente
    private class ClientClass extends Thread{
        private BluetoothDevice device; //para o lado cliente precisamos de um dispositivo
        private BluetoothSocket socket; // e um socket

        public ClientClass(BluetoothDevice device1){
            device = device1;
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID); //na inicializacao desse socket so eh necessario o uuid
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            try {
                socket.connect(); //tenta conectar a um dispositivo
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceiver = new SendReceiver(socket);
                sendReceiver.start(); //se obteve sucesso podemos inicializar a classe que trata o envio e recebimento de msgs
            } catch (IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }

    //classe que cuida do envio e recebimento de msgs
    private class SendReceiver extends Thread{
        private final BluetoothSocket bluetoothSocket; //inicializamos um BT socket para gerenciar a conexao
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceiver(BluetoothSocket socket){
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn = bluetoothSocket.getInputStream(); //tenta pegar entradas
                tempOut = bluetoothSocket.getOutputStream(); //tenta pegar saidas
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream = tempIn;
            outputStream = tempOut;
        }

        public void run(){
            byte[] buffer = new byte[1024];
            int bytes;

            while (true){ //enquanto a conexao estiver aberta portanto usamos true
                try {
                    bytes = inputStream.read(buffer); //le os bytes do inputstream e guarda no buffer (retorna -1 no final)
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget(); //le os bytes do objeto buffer ate chegar no -1 (fim) e envia ao handler
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes){
            try {
                outputStream.write(bytes); //"joga" ou escreve os bytes nesse estream
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}