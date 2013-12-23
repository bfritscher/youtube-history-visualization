//https://github.com/square/crossfilter/wiki/API-Reference
//https://github.com/square/crossfilter/blob/gh-pages/index.html
//https://github.com/NickQiZhu/dc.js/blob/master/wiki/api.md#base-chart
//https://github.com/mbostock/d3/wiki/API-Reference
var fullText;
var uploader;
var totalTime;
var watchDate;
var hoursChart;
var durationChart;
var categoryChart;
var dayChart;
var daysChart;

var dateFormat = d3.time.format("%d-%m-%Y");
var timeFormat = function(seconds){
	var days = Math.floor(seconds / 3600 / 24);
	var hours = Math.floor((seconds/3600) - (days*24));
	var minutes = Math.floor((seconds/60) - (hours*60) - (days*24*60));
	var str = "";
	if(days > 0){
		str += days + "d ";
	}
	return str + hours + ":" + minutes;
};
var numberFormat = d3.format(",d");

function nl2br (str, is_xhtml) {
    var breakTag = (is_xhtml || typeof is_xhtml === 'undefined') ? '<br />' : '<br>';
    return (str + '').replace(/([^>\r\n]?)(\r\n|\n\r|\r|\n)/g, '$1' + breakTag + '$2');
}

function drawList(start, end){
	var size = 9;
	var data = watchDate.top(Infinity);
	start = start || 0;
	end = end || Math.min(size, data.length);
	d3.select('#list-widget .progress').style('width',  (end/data.length*100) + '%');
	
	var item = d3.select("#list").selectAll(".item")
		.data(data.slice(start, end), function(d){return d.id;});	
	var div = item.enter().append("div").attr('class','item');
	div.append('span');
	div.style('background', function(d){ return "url('http://img.youtube.com/vi/"+ d.video_id + "/mqdefault.jpg')"});
	div.on('click', function(d){
		var detail = d3.select('#detail');
		detail.select('iframe').attr('src', '//www.youtube.com/embed/' + d.video_id);
		detail.select('.description').html(linkify.linkify(nl2br(d.description)));
		detail.select('.watchdate').text(dateFormat(d.watchdate));
		detail.select('.uploaddate').text(dateFormat(d.uploadeddate));
		detail.select('.views').text(numberFormat(d.views));
		detail.select('.category').text(d.category);
		detail.select('a.uploader').attr('href','//youtube.com/channel/' + d.uploader).text(d.uploader_name);
		detail.style('display', 'block');
		d3.select('#detail-background').style('display', 'block');
	});
	div.append('a').attr('href','javascript:;');
	item.select('span').text(function(d){return d.title;});
	item.select('a').text(function(d){return d.uploader_name;})
	.on('click', function(d){
		d3.event.stopPropagation();
		uploaderChart.filter(d.uploader);
		dc.redrawAll();
	});
	item.exit().remove();
	item.order(); //important
	
	d3.select('.start').text(start+1)
	.on('click', function(){
		if(start-size >= 0){
			drawList(start - size, start);
		}
	});
	d3.select('.end').text(Math.min(end, data.length))
	.on('click', function(){
		if(end+size <= data.length+size-1){
			drawList(end, Math.min(end + size, data.length));
		}
	});
}	

d3.json("api.groovy", function(data) {
	data.forEach(function(d,i){
		d.watchdate = new Date(d.watchdate);
		d.uploadeddate = new Date(d.uploadeddate);
		d.day = d3.time.day(d.watchdate);
	});
	
	//create crossfilter
	var entry = crossfilter(data),
		all = entry.groupAll();
		
	totalTime = entry.groupAll().reduceSum(function(d){return d.duration;});
	
	fullText = entry.dimension(function(d){ return (d.title + " " + d.description).toLowerCase(); });
	d3.select('#search').on('keyup', function(){
		var q = this.value.toLowerCase();
		fullText.filter(function(d){ return d.indexOf(q) > -1;});
		dc.redrawAll();
	});
	
	d3.select('#list-widget').on('mousewheel', function(){
		d3.event.preventDefault();
		if(d3.event.wheelDelta > 0){
			d3.select('.start').on('click')();
		}else{
			d3.select('.end').on('click')();
		}
	});
	
	d3.select('#detail-background').on('click', function(){
		d3.select('#detail-background').style('display', 'none');
		d3.select('#detail').style('display', 'none');
	});
	
	watchDate = entry.dimension(function(d){return d.watchdate});
	uploader = entry.dimension(function(d){return d.uploader});
	// define a dimension
	var category = entry.dimension(function(d) { return d.category; });
	// map/reduce to group sum
	var categoryGroup = category.group().reduceCount();
		
	var hour = entry.dimension(function(d) { return d.watchdate.getHours() + d.watchdate.getMinutes() / 60; }),
    hourGroup = hour.group(Math.floor);
	
	var dayFormat = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
	var day = entry.dimension(function(d) { return (d.watchdate.getDay()+6) % 7; }),
    dayGroup = day.group().reduceCount();
	
	var days = entry.dimension(function(d) { return d.day; }),
    daysGroup = days.group().reduceCount();
		
	var duration = entry.dimension(function(d) { return Math.round(d.duration / (60 *5)) * 5; }),
    durationGroup = duration.group().reduceCount();
		
	hoursChart = dc.barChart("#hours")
    .width(400) // (optional) define chart width, :default = 200
    .height(200) // (optional) define chart height, :default = 200
    .transitionDuration(500) // (optional) define chart transition duration, :default = 500
    // (optional) define margins
    .margins({top: 10, right: 50, bottom: 30, left: 40})
    .dimension(hour) // set dimension
    .group(hourGroup) // set group
    .elasticY(true)
    .elasticX(false)
    .x(d3.scale.linear().domain([0, 24]))
    .round(Math.floor)
    .renderHorizontalGridLines(true)
    //.renderVerticalGridLines(true)
    .brushOn(true)
	.colors(["#cc181e"]);
	hoursChart.xAxis().ticks(24);
	
	durationChart = dc.barChart("#duration")
    .width(250) // (optional) define chart width, :default = 200
    .height(200) // (optional) define chart height, :default = 200
    .transitionDuration(500) // (optional) define chart transition duration, :default = 500
    // (optional) define margins
    .margins({top: 10, right: 50, bottom: 30, left: 40})
    .dimension(duration) // set dimension
    .group(durationGroup) // set group
    .elasticY(true)
    .elasticX(false)
    .x(d3.scale.linear().domain([0, 80]))
	.xUnits(function(start, end, xDomain) {
		return Math.abs(end - start)/5;
	})
    .renderHorizontalGridLines(true)
    //.renderVerticalGridLines(true)
    .brushOn(true)
	.colors(["#cc181e"]);
		
	categoryChart = dc.rowChart("#category")
	.group(categoryGroup) // set group
    .dimension(category) // set dimension
    .width(180) // (optional) define chart width, :default = 200
    .height(400) // (optional) define chart height, :default = 200
	.margins({top: 10, right: 50, bottom: 30, left: 10})
	.colors(["#cc181e"])		 
    .renderLabel(true)
    .renderTitle(true)
	.labelOffsetY(13)
	.labelOffsetX(3)
	.elasticX(true);
	categoryChart.xAxis().ticks(4);
	
	categoryChart.on('preRedraw', function(chart){
		// smooth the rendering through event throttling
		drawList();
		d3.select('.totaltime').text(timeFormat(totalTime.value()));
	});

	dayChart = dc.rowChart("#day")
	.group(dayGroup) // set group
    .dimension(day) // set dimension
    .width(180) // (optional) define chart width, :default = 200
    .height(200) // (optional) define chart height, :default = 200
	.margins({top: 10, right: 50, bottom: 30, left: 10})
	.colors(["#cc181e"])		 
    .renderLabel(true)
	.label(function(d){
		return dayFormat[d.key];
	})
    .renderTitle(true)
	.labelOffsetY(13)
	.labelOffsetX(3)
	.elasticX(true);
    dayChart.xAxis().ticks(4);
    
	daysOverviewChart = dc.barChart("#days-overview")
	.width(990)
	.height(40)
	.colors(["#cc181e"])	
	.margins({top: 0, right: 50, bottom: 20, left: 40})
	.dimension(days)
	.group(daysGroup)
	.centerBar(true)
	.gap(1)
	.x(d3.time.scale().domain([days.bottom(1)[0].watchdate, days.top(1)[0].watchdate]))
	.round(d3.time.day.round)
	.xUnits(d3.time.days);
	
	daysChart = dc.barChart("#days")	
	.width(990)
	.height(200)
	.colors(["#cc181e"])	
	.transitionDuration(1000)
	.margins({top: 10, right: 50, bottom: 25, left: 40})
	.dimension(days)
	.group(daysGroup)
	.mouseZoomable(true)
	.x(d3.time.scale().domain([days.bottom(1)[0].watchdate, days.top(1)[0].watchdate]))
	.round(d3.time.day.round)
	.xUnits(d3.time.days)
	.elasticY(true)
	.renderHorizontalGridLines(true)
	.brushOn(false)
	.rangeChart(daysOverviewChart);
	
	uploaderChart = dc.baseChart({})	
	.dimension(uploader)
	.group(all)
	
	
	dc.dataCount("#list-widget")
    .dimension(entry)
    .group(all);
	
	dc.renderAll();
	dc.redrawAll();
	d3.select('#detail-background').style('display', 'none').style('opacity', '');
});