package service.impl;


import dao.PacketDao;
import dto.PacketsInfo;
import dto.StorageByDestinationInTimeDomain;
import dto.ValueInTimeInterval;
import entities.Packet;
import exceptions.GeneralException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.DataService;

import javax.transaction.Transactional;
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service("dataService")
public class DataServiceImpl implements DataService {

    @Autowired
    private PacketDao packetDao;

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public void uploadFileToDatabase(String[] fileNames) throws GeneralException {
        int id = packetDao.getMaxPacketId() + 1;
        for (int i = 0, j = 1; i < fileNames.length; i++) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(fileNames[i]));
                String line;
                List<Packet> temp = new ArrayList<Packet>();
                while ((line = br.readLine()) != null) {
                    /* Gaunam paketą iš eilutės */
                    Packet p = getPacket(line, i);
                    p.setId(id);
                    temp.add(p);

                    if (j % 1000 == 0) {
                        packetDao.insertPackets(temp);
                        temp = new ArrayList<Packet>();
                    }
                    j++; id++;
                }
                packetDao.insertPackets(temp);
            } catch (Exception e) {
                throw new GeneralException("Could not upload file to the database!", e);
            }
        }
    }

    @Override
    @Transactional
    public String getEntropy(Timestamp start, Timestamp end, Integer increment, Integer windowWidth) throws GeneralException {
        List<PacketsInfo> packetsInfo = packetDao.findPacketCounts(start, end, increment);

        StorageByDestinationInTimeDomain storage = new StorageByDestinationInTimeDomain();

        if (packetsInfo != null && !packetsInfo.isEmpty()) { //Tiesiog patikrinam ar grįžo kas nors iš duombazės
            storage.setWindowWidth(windowWidth); //Nustatome lango plotį, kitaip būtų naudojamas defaultinis

            for (int i = 0; i < packetsInfo.size(); i++) { //Važiuojam per paketų informaciją saugojančius objektus
                PacketsInfo pi = packetsInfo.get(i);

                /* Jeigu apdorojamo objekto laikas pasikeitė, tarkim visi prieš tai apdorojami paketai
                * buvo iš pirmos sekundės, o dabar jau gavome paketą iš antros sekundės, tai dabartinio
                * interalo paketus išsisaugojame atminyje, ir išvalome dabartinio laiko paketų masyvą,
                * kad galėtumėme saugoti naujus paketus */
                if (storage.timeExceedsCurrentTime(pi.getTime())) {
                    storage
                            .addCurrentIntervalToStorage()
                            .cleanCurrentInterval();
                }

                /* Tiesiog paduodame dabartinį objektą apdorojimui */
                storage.addNewPacketInfo(pi);
            }
        }

        /* Pasiemame entropijos reikšmes */
        List<ValueInTimeInterval> valuesInTimeIntervals = storage.getListOfEntropies();
        StringBuilder result = new StringBuilder();
        for (ValueInTimeInterval v: valuesInTimeIntervals) {
            result.append(v.getTime()).append("\t").append(v.getValue()).append("\n");
        }
        return result.toString();
    }

    private Packet getPacket(String line, int fileIndex) throws GeneralException {
        /* Suskaidom eilutę pagal "," */
        String[] cols = line.split("\\\",\\\"");

        /* Nuo pirmo stulpelio pašalinam kabutes*/
        cols[0] = cols[0].substring(1);

        /* Nuo paskutinio stulpelio pašalinam kabutes*/
        cols[cols.length - 1] = cols[cols.length - 1].substring(0, cols[cols.length - 1].length() - 1);

        Packet packet = new Packet();

        // Stulpelių indeksai ---> [NR, LAIKAS, ŠALTINIO IP, PASKIRTIES IP, PROTOKOLAS]
        int timestampColIndex = 1;
        int sourceColIndex = 2;
        int destinationColIndex = 3;
        int protocolColIndex = 4;

        Timestamp timestamp = getTimestampFromString(cols[timestampColIndex]);
        packet.setTimestamp(timestamp);
        packet.setSource(cols[sourceColIndex]);
        packet.setDestination(cols[destinationColIndex]);
        packet.setProtocol(cols[protocolColIndex]);

        return packet;
    }

    private Timestamp getTimestampFromString(String timestampString) throws GeneralException {
        try {
            String[] parts = timestampString.split("\\.");
            Date parsedDate = simpleDateFormat.parse(parts[0]);
            Timestamp timestamp = new Timestamp(parsedDate.getTime());
            timestamp.setNanos(Integer.parseInt(parts[1]) * 1000);
            return timestamp;
        } catch (Exception e) {
            throw new GeneralException("Blogas datos formatas!", e);
        }
    }

}