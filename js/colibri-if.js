// Javascript function to provide an interface to handle
// raw WebRTC stuff that came to us from a browser over
// the rabbit interface.

var SdpToIq = function(sdpin) {
    this.peers = [];
    var self = this;

    var elem = window.$iq({to: "thebridge", type: 'get'});
    elem.c('conference', {xmlns: 'http://jitsi.org/protocol/colibri'});

    this.media = ['video'];

    this.media.forEach(function (name) {
        elem.c('content', {name: name});
        elem.c('channel', {
            initiator: 'true',
            expire: '15',
            endpoint: 'fix_me_focus_endpoint'}).up();
        for (var j = 0; j < self.peers.length; j++) {
            elem.c('channel', {
                initiator: 'true',
                expire: '15',
                endpoint: self.peers[j].substr(1 + self.peers[j].lastIndexOf('/'))
            }).up();
        }
        elem.up(); // end of content
    });

    var localSDP = new SDP(sdpin);
    localSDP.media.forEach(function (media, channel) {
        var name = SDPUtil.parse_mline(media.split('\r\n')[0]).media;
        elem.c('content', {name: name});
        elem.c('channel', {initiator: 'false', expire: '15'});

        // FIXME: should reuse code from .toJingle
        var mline = SDPUtil.parse_mline(media.split('\r\n')[0]);
        for (var j = 0; j < mline.fmt.length; j++) {
            var rtpmap = SDPUtil.find_line(media, 'a=rtpmap:' + mline.fmt[j]);
            elem.c('payload-type', SDPUtil.parse_rtpmap(rtpmap));
            elem.up();
        }

        localSDP.TransportToJingle(channel, elem);

        elem.up(); // end of channel
        for (j = 0; j < self.peers.length; j++) {
            elem.c('channel', {initiator: 'true', expire:'15' }).up();
        }
        elem.up(); // end of content
    });

    return elem;
};
