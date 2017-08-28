/*
 * Copyright (C) 2014 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */
package au.org.ala

import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.maxmind.geoip2.model.CityResponse
import com.maxmind.geoip2.record.Location
import groovy.util.logging.Log4j

import javax.annotation.PostConstruct
import javax.servlet.http.HttpServletRequest

/**
 * Provides methods to access MaxMind GeoIP2 API (http://maxmind.github.io/GeoIP2-java/)
 * Some code for getIpAddress has been taken from https://github.com/donbeave/grails-geoip/
 * Download City DB from https://dev.maxmind.com/geoip/geoip2/geolite2/

 * DB Download should be automated and installed to grailsApplication.config.geopip.database.path as part of
 * ala-install ansible playbooks
 * @author Javier Molina
 */
@Log4j
class GeoIpService {
    DatabaseReader reader

    def grailsApplication

    @PostConstruct
    void init() {
        //
        // Path to "GeoIP2-City.mmdb"
        String filePath = grailsApplication.config.geopip.database.path

        // A File object pointing to your GeoIP2 or GeoLite2 database
        File fileDatabase = new File(filePath)

        if(!fileDatabase.exists() || !fileDatabase.canRead()) {
            log.warn("GeoIP Database file [${filePath}] does not exist or can't be read. GeoIpservice will be bypassed.")
            return
        }

        // This creates the DatabaseReader object, which should be reused across
        // lookups.

        try {
            reader = new DatabaseReader.Builder(fileDatabase).withCache(new CHMCache()).build()
        } catch (Exception ex) {
            log.error("Unable to open GeoIp Database [${filePath}]. GeoIpservice will be bypassed.", ex)
        }
    }

    static List<String> ipHeaders = ['X-Real-IP',
                                     'Client-IP',
                                     'X-Forwarded-For',
                                     'Proxy-Client-IP',
                                     'WL-Proxy-Client-IP',
                                     'rlnclientipaddr']

    private InetAddress getIpAddress(HttpServletRequest  request) {
        String unknown = 'unknown'
        String ipAddressStr = unknown

        ipHeaders.each { header ->
            if (!ipAddressStr || unknown.equalsIgnoreCase(ipAddressStr))
                ipAddressStr = request.getHeader(header)
        }

        if (!ipAddressStr)
            ipAddressStr = request.remoteAddr

        InetAddress.getByName(ipAddressStr);

    }

    /**
     * gets Obtains the Location for a client request
     * @param request the client request
     * @return the Location for the requesting client IP Address or null if the GeoIP database could not be found.
     */
    Location getLocation(HttpServletRequest request ) {
        if(reader) {
            try {
                InetAddress ipAddress = getIpAddress(request)
                CityResponse response = reader.city(ipAddress);
                return response.location
            } catch (AddressNotFoundException ex) {
                log.warn(ex.message)
                log.debug("Error getting location. ", ex)
            }
        }
        return null
    }
}
