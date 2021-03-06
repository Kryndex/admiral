/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DocumentService } from '../../utils/document.service';

export class BaseDetailsComponent implements OnInit {
  id;
  entity;
  protected projectLink: string;

  constructor(protected route: ActivatedRoute, protected service: DocumentService, protected link: string) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
       this.id = params['id'];

       if (!this.id) {
           // no need to retrieve data
         return;
       }

       this.service.getById(this.link, this.id, this.projectLink).then(service => {
         this.entity = service;
         this.entityInitialized();
       });
    });

    // try with the parent
    this.route.parent.params.subscribe(params => {
        this.id = params['id'];

          if (!this.id) {
              // no need to retrieve data
              return;
          }

          this.service.getById(this.link, this.id, this.projectLink).then(service => {
              this.entity = service;
              this.entityInitialized();
          });
      });
  }

  protected entityInitialized() {

  }
}
