# frozen_string_literal: true

# Licensed to the Software Freedom Conservancy (SFC) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The SFC licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

require_relative 'spec_helper'

module Selenium
  module WebDriver
    describe DevTools, exclusive: {browser: %i[chrome edge firefox_nightly]} do
      let(:username) { SpecSupport::RackServer::TestApp::BASIC_AUTH_CREDENTIALS.first }
      let(:password) { SpecSupport::RackServer::TestApp::BASIC_AUTH_CREDENTIALS.last }

      before(:all) { quit_driver }

      after { quit_driver }

      it 'sends commands' do
        driver.devtools.page.navigate(url: url_for('xhtmlTest.html'))
        expect(driver.title).to eq("XHTML Test Page")
      end

      it 'supports events' do
        callback = instance_double(Proc, call: nil)

        driver.devtools.page.enable
        driver.devtools.page.on(:load_event_fired) { callback.call }
        driver.navigate.to url_for('xhtmlTest.html')
        sleep 0.5

        expect(callback).to have_received(:call).at_least(:once)
      end

      context 'authentication', except: {browser: :firefox_nightly,
                                         reason: 'Fetch.enable is not yet supported'} do
        it 'on any request' do
          driver.register(username: username, password: password)

          driver.navigate.to url_for('basicAuth')
          expect(driver.find_element(tag_name: 'h1').text).to eq('authorized')
        end

        it 'based on URL' do
          auth_url = url_for('basicAuth')
          driver.register(username: username, password: password, uri: /localhost/)

          driver.navigate.to auth_url.sub('localhost', '127.0.0.1')
          expect { driver.find_element(tag_name: 'h1') }.to raise_error(Error::NoSuchElementError)

          driver.navigate.to auth_url
          expect(driver.find_element(tag_name: 'h1').text).to eq('authorized')
        end
      end

      it 'notifies about log messages' do
        logs = []
        driver.on_log_event(:console) { |log| logs.push(log) }
        driver.navigate.to url_for('javascriptPage.html')

        driver.execute_script("console.log('I like cheese');")
        sleep 0.5
        driver.execute_script("console.log(true);")
        sleep 0.5
        driver.execute_script("console.log(null);")
        sleep 0.5
        driver.execute_script("console.log(undefined);")
        sleep 0.5
        driver.execute_script("console.log(document);")
        sleep 0.5

        expect(logs).to include(
          an_object_having_attributes(type: :log, args: ['I like cheese']),
          an_object_having_attributes(type: :log, args: [true]),
          an_object_having_attributes(type: :log, args: [nil]),
          an_object_having_attributes(type: :log, args: [{'type' => 'undefined'}])
        )
      end

      it 'notifies about document log messages', except: {browser: :firefox_nightly,
                                                          reason: 'Firefox & Chrome parse document differently'} do
        logs = []
        driver.on_log_event(:console) { |log| logs.push(log) }
        driver.navigate.to url_for('javascriptPage.html')

        driver.execute_script("console.log(document);")
        wait.until { !logs.empty? }

        expect(logs).to include(
          an_object_having_attributes(type: :log, args: [hash_including('type' => 'object')])
        )
      end

      it 'notifies about document log messages', only: {browser: :firefox_nightly,
                                                        reason: 'Firefox & Chrome parse document differently'} do
        logs = []
        driver.on_log_event(:console) { |log| logs.push(log) }
        driver.navigate.to url_for('javascriptPage.html')

        driver.execute_script("console.log(document);")
        wait.until { !logs.empty? }

        expect(logs).to include(
          an_object_having_attributes(type: :log, args: [hash_including('location')])
        )
      end

      it 'notifies about exceptions' do
        exceptions = []
        driver.on_log_event(:exception) { |exception| exceptions.push(exception) }
        driver.navigate.to url_for('javascriptPage.html')

        driver.find_element(id: 'throwing-mouseover').click
        wait.until { exceptions.any? }

        exception = exceptions.first
        expect(exception.description).to include('Error: I like cheese')
        expect(exception.stacktrace).not_to be_empty
      end

      it 'notifies about DOM mutations', except: {browser: :firefox_nightly,
                                                  reason: 'Runtime.addBinding not yet supported'} do
        mutations = []
        driver.on_log_event(:mutation) { |mutation| mutations.push(mutation) }
        driver.navigate.to url_for('dynamic.html')

        driver.find_element(id: 'reveal').click
        wait.until { mutations.any? }

        mutation = mutations.first
        expect(mutation.element).to eq(driver.find_element(id: 'revealed'))
        expect(mutation.attribute_name).to eq('style')
        expect(mutation.current_value).to eq('')
        expect(mutation.old_value).to eq('display:none;')
      end

      context 'network interception', except: {browser: :firefox_nightly,
                                               reason: 'Fetch.enable is not yet supported'} do
        it 'allows to continue requests' do
          requests = []
          driver.intercept do |request|
            requests << request
            request.continue
          end
          driver.navigate.to url_for('html5Page.html')
          expect(driver.title).to eq('HTML5')
          expect(requests).not_to be_empty
        end

        it 'allows to stub responses' do
          requests = []
          driver.intercept do |request|
            requests << request
            request.respond(body: '<title>Intercepted!</title>')
          end
          driver.navigate.to url_for('html5Page.html')
          expect(driver.title).to eq('Intercepted!')
          expect(requests).not_to be_empty
        end

        it 'intercepts specific requests' do
          stubbed = []
          continued = []
          driver.intercept do |request|
            if request.method == 'GET' && request.url.include?('resultPage.html')
              stubbed << request
              request.respond(body: '<title>Intercepted!</title>')
            else
              continued << request
              request.continue
            end
          end

          driver.navigate.to url_for('formPage.html')
          expect(driver.title).to eq('We Leave From Here')
          expect(stubbed).to be_empty
          expect(continued).not_to be_empty

          driver.find_element(id: 'submitButton').click
          expect(driver.title).to eq('Intercepted!')
          expect(stubbed).not_to be_empty
        end
      end
    end
  end
end
