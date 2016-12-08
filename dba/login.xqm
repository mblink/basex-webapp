(:~
 : Code for logging in and out.
 :
 : @author Christian Grün, BaseX Team, 2014-16
 :)
module namespace dba = 'dba/login';

import module namespace Session = 'http://basex.org/modules/session';
import module namespace cons = 'dba/cons' at 'modules/cons.xqm';
import module namespace html = 'dba/html' at 'modules/html.xqm';
import module namespace tmpl = 'dba/tmpl' at 'modules/tmpl.xqm';

(:~
 : Login page.
 : @param  $name   user name (optional)
 : @param  $error  error string (optional)
 : @param  $path   path to redirect to (optional)
 : @return page
 :)
declare
  %rest:path("/dba/login")
  %rest:query-param("name" , "{$name}")
  %rest:query-param("error", "{$error}")
  %rest:query-param("path",  "{$path}")
  %output:method("html")
function dba:welcome(
  $name   as xs:string?,
  $error  as xs:string?,
  $path   as xs:string?
) as element(html) {
  tmpl:wrap(map { 'error': $error },
    <tr>
      <td>
        <form action="login-check" method="post">
          <input type="hidden" name="path" value="{ $path }"/>
          <div class='note'>
            Enter your admin credentials:
          </div>
          <div class='small'/>
          <table>
            <tr>
              <td><b>Name:</b></td>
              <td>
                <input size="30" name="name" value="{ $name }" id="user"/>
                { html:focus('user') }
              </td>
            </tr>
            <tr>
              <td><b>Password:</b></td>
              <td>
                <input size="30" type="password" name="pass"/>
                { html:button('login', 'Login') }
              </td>
            </tr>
          </table>
        </form>
      </td>
    </tr>
  )
};

(:~
 : Checks the user input and redirects to the main page, or back to the login page.
 : @param  $name  user name
 : @param  $pass  password
 : @param  $path  path to redirect to (optional)
 : @return redirect
 :)
declare
  %rest:path("/dba/login-check")
  %rest:query-param("name", "{$name}")
  %rest:query-param("pass", "{$pass}")
  %rest:query-param("path", "{$path}")
function dba:login(
  $name  as xs:string,
  $pass  as xs:string,
  $path  as xs:string?
) as element(rest:response) {
  try {
    user:check($name, $pass),
    if(user:list-details($name)/@permission != 'admin') then (
      dba:reject($name, 'Admin credentials required.', $path)
    ) else (
      dba:accept($name, $pass, $path)
    )
  } catch user:* {
    dba:reject($name, 'Please check your login data.', $path)
  }
};

(:~
 : Ends a session and redirects to the login page.
 : @return redirect
 :)
declare
  %rest:path("/dba/logout")
function dba:logout(
) as element(rest:response) {
  let $name := $cons:SESSION/name
  return (
    admin:write-log('DBA user was logged out: ' || $name),
    Session:delete($cons:SESSION-KEY),
    web:redirect("/dba/login", map { 'name': $name })
  )
};

(:~
 : Accepts a user and redirects to the main page.
 : @param  $name  entered user name
 : @param  $path  path to redirect to
 : @return redirect
 :)
declare %private function dba:accept(
  $name  as xs:string,
  $pass  as xs:string,
  $path  as xs:string?
) {
  Session:set($cons:SESSION-KEY,
    element dba-session {
      element name { $name },
      element pass { $pass }
    }
  ),
  admin:write-log('DBA user was logged in: ' || $name),
  web:redirect(if($path) then $path else "databases")
};

(:~
 : Rejects a user and redirects to the login page.
 : @param  $name     entered user name
 : @param  $message  error message
 : @param  $path     path to redirect to
 : @return redirect
 :)
declare %private function dba:reject(
  $name     as xs:string,
  $message  as xs:string,
  $path     as xs:string?
) as element(rest:response) {
  admin:write-log('DBA login was denied: ' || $name),
  web:redirect("login", map { 'name': $name, 'error': $message, 'path': $path })
};
