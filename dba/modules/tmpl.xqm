(:~
 : Template functions.
 :
 : @author Christian Grün, BaseX Team, 2014-17
 :)
module namespace tmpl = 'dba/tmpl';

import module namespace cons = 'dba/cons' at 'cons.xqm';
import module namespace util = 'dba/util' at 'util.xqm';

(:~
 : Extends the specified table rows with the page template.
 : @param  $rows  tr elements
 : @return HTML page
 :)
declare function tmpl:wrap(
  $rows  as element(tr)+
) as element(html) {
  tmpl:wrap(map { }, $rows)
};

(:~
 : Extends the specified table rows with the page template.
 : The following options can be specified:
 : <ul>
 :   <li><b>top</b>: current top category</li>
 :   <li><b>error</b>: error string</li>
 :   <li><b>info</b>: info string</li>
 : </ul>
 : @param  $options  options
 : @param  $rows     tr elements
 : @return page
 :)
declare function tmpl:wrap(
  $options  as map(*),
  $rows     as element(tr)+
) as element(html) {
  let $top := $options('cat') ! util:capitalize(.)
  return <html>
    <head>
      <meta charset="utf-8"/>
      <title>DBA{ $top ! (' • ' || .) }</title>
      <meta name="description" content="Database Administration"/>
      <meta name="author" content="BaseX Team, 2014-17"/>
      <link rel="stylesheet" type="text/css" href="static/style.css"/>
      { $options('css') ! <link rel="stylesheet" type="text/css" href="static/{.}"/> }
      <script type="text/javascript" src="static/js.js"/>
      { $options('scripts') ! <script type="text/javascript" src="static/{.}"/> }
    </head>
    <body>
      <div class="right"><img style='padding-left:10px;padding-bottom:10px;'
        src="static/basex.svg"/></div>
      <h1>Database Administration</h1>
      <div>{
        let $emph := <span>{
          element b {
            attribute id { 'info' },
            let $error := $options?error[.], $info := $options?info[.]
            return if($error) then (
              attribute class { 'error' }, $error
            ) else if($info) then (
              attribute class { 'info' }, $info
            ) else (),
            '&#xa0;'
          }
        }</span>
        return try {
          cons:check(),
          let $cats :=
            for $cat in ('Databases', 'Queries', 'Files', 'Logs',  'Users',
              'Settings', 'Logout')
            let $link := <a href="{ lower-case(replace($cat, ' &amp; ', '-')) }">{ $cat }</a>
            return if($top = $link) then (
              <b>{ $link }</b>
            ) else (
              $link
            )
          return (head($cats), tail($cats) ! (' | ', .)),
          (1 to 3) ! '&#x2000;',
          $emph
        } catch basex:login {
          $emph
        },
        $cons:SESSION-VALUE ! <span style='float:right'>User: <b>{ . }</b></span>
      }</div>
      <hr/>
      <table width='100%'>{ $rows }</table>
      <hr/>
      <div class='right'><sup>BaseX Team, 2014-17</sup></div>
      <div class='small'/>
      <script type="text/javascript">(function(){{ buttons(); }})();</script>
    </body>
  </html>
};
